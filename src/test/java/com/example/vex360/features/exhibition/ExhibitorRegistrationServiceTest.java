package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.exhibition.services.impl.ExhibitorRegistrationServiceImpl;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.entities.Exhibition;
import com.example.vex360.shared.entities.ExhibitionPackage;
import com.example.vex360.shared.entities.ExhibitorRegistration;
import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.entities.Payment;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@ExtendWith(MockitoExtension.class)
public class ExhibitorRegistrationServiceTest {

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private ExhibitionPackageRepository packageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PayOSIntegrationService payOSIntegrationService;

    @Mock
    private BoothProvisioningService boothProvisioningService;

    @InjectMocks
    private ExhibitorRegistrationServiceImpl registrationService;

    private User companyUser;
    private ExhibitionPackage paidPackage;
    private ExhibitionPackage freePackage;
    private ExhibitionPackage invalidPackage;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(registrationService, "returnUrl", "http://localhost:5173/payment/success");
        ReflectionTestUtils.setField(registrationService, "cancelUrl", "http://localhost:5173/payment/cancel");

        companyUser = User.builder()
                .id(UUID.randomUUID())
                .email("company@example.com")
                .fullName("Test Company")
                .build();

        PackageTemplate template = PackageTemplate.builder()
                .id(UUID.randomUUID())
                .name("Standard Package")
                .price(BigDecimal.valueOf(1000000))
                .build();

        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .name("Tech Expo 2026")
                .build();

        paidPackage = ExhibitionPackage.builder()
                .id(10)
                .template(template)
                .exhibition(exhibition)
                .finalPrice(BigDecimal.valueOf(1500000)) // finalPrice >= floorPrice (1M)
                .build();

        freePackage = ExhibitionPackage.builder()
                .id(11)
                .template(PackageTemplate.builder()
                        .id(UUID.randomUUID())
                        .price(BigDecimal.ZERO)
                        .build())
                .exhibition(exhibition)
                .finalPrice(BigDecimal.ZERO)
                .build();

        invalidPackage = ExhibitionPackage.builder()
                .id(12)
                .template(template)
                .exhibition(exhibition)
                .finalPrice(BigDecimal.valueOf(500000)) // finalPrice < floorPrice (1M)
                .build();
    }

    @Test
    public void testInitializeRegistration_PriceValidationFailed() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(companyUser));
        when(packageRepository.findById(12)).thenReturn(Optional.of(invalidPackage));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.initializeRegistration(companyUser.getId(), 12);
        });

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void testInitializeRegistration_FreePackage_Success() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(companyUser));
        when(packageRepository.findById(11)).thenReturn(Optional.of(freePackage));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var registration = registrationService.initializeRegistration(companyUser.getId(), 11);

        assertNotNull(registration);
        assertEquals(ExhibitorRegistrationStatus.APPROVED, registration.getStatus());

        verify(registrationRepository).save(any());
        verify(paymentRepository).save(any());
        verify(boothProvisioningService).ensureBoothForApprovedRegistration(registration);
        verify(payOSIntegrationService, never()).createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    public void testInitializeRegistration_PaidPackage_Success() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(companyUser));
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.save(any())).thenAnswer(invocation -> {
            var reg = (ExhibitorRegistration) invocation.getArgument(0);
            reg.setId(99); // assign ID
            return reg;
        });
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentLinkResponse payOSResponse = CreatePaymentLinkResponse.builder()
                .bin("970407")
                .accountNumber("123456789")
                .accountName("Tech Expo Organizer")
                .amount(1500000L)
                .description("Dang ky trien lam")
                .orderCode(123456L)
                .currency("VND")
                .paymentLinkId("link_123")
                .status(vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PENDING)
                .expiredAt(0L)
                .checkoutUrl("https://pay.payos.vn/web/checkout-xyz")
                .qrCode("qr_code_data")
                .build();

        when(payOSIntegrationService.createPaymentLink(
                anyLong(),
                eq(1500000L),
                anyString(),
                eq("http://localhost:5173/payment/success"),
                eq("http://localhost:5173/payment/cancel")
        )).thenReturn(payOSResponse);

        var registration = registrationService.initializeRegistration(companyUser.getId(), 10);

        assertNotNull(registration);
        assertEquals(ExhibitorRegistrationStatus.PENDING, registration.getStatus());

        verify(registrationRepository).save(any());
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(payOSIntegrationService).createPaymentLink(anyLong(), eq(1500000L), anyString(), anyString(), anyString());
    }
}
