package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.company.dtos.request.CreateStoragePackageOrderRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageOrderResponseDTO;
import com.example.vex360.features.company.dtos.response.StoragePackageResponseDTO;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.entities.StoragePackage;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.StoragePackageRepository;
import com.example.vex360.features.exhibition.services.StoragePaymentService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class StoragePackageServiceUnitTest {

    @Mock
    private StoragePackageRepository storagePackageRepository;

    @Mock
    private StoragePackageOrderRepository storagePackageOrderRepository;

    @Mock
    private StoragePaymentService storagePaymentService;

    @Mock
    private CompanyService companyService;

    @InjectMocks
    private StoragePackageService storagePackageService;

    private User currentUser;
    private Company company;
    private StoragePackage storagePackage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storagePackageService, "returnUrl", "http://return");
        ReflectionTestUtils.setField(storagePackageService, "cancelUrl", "http://cancel");

        currentUser = User.builder().id(UUID.randomUUID()).email("user@example.com").build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Company X")
                .storageUsedBytes(200L)
                .storageQuotaBytes(1000L)
                .build();
        storagePackage = StoragePackage.builder()
                .id(1)
                .name("Gold package")
                .description("Desc")
                .quotaBytes(2000L)
                .priceVnd(100000L)
                .isActive(true)
                .build();
    }

    @Test
    void testListActivePackages_Success() {
        when(storagePackageRepository.findByIsActiveTrueOrderByPriceVndAsc()).thenReturn(List.of(storagePackage));

        List<StoragePackageResponseDTO> response = storagePackageService.listActivePackages();

        assertEquals(1, response.size());
        assertEquals("Gold package", response.get(0).getName());
        assertEquals(2000L, response.get(0).getQuotaBytes());
        assertEquals(100000L, response.get(0).getPriceVnd());
    }

    @Test
    void testCreateOrder_PackageNotFound_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        when(storagePackageRepository.findById(1)).thenReturn(Optional.empty());

        CreateStoragePackageOrderRequest request = new CreateStoragePackageOrderRequest();
        request.setPackageId(1);

        AppException exception = assertThrows(AppException.class, () -> {
            storagePackageService.createOrder(currentUser, request);
        });

        assertEquals(ErrorCode.STORAGE_PACKAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testCreateOrder_Success() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        when(storagePackageRepository.findById(1)).thenReturn(Optional.of(storagePackage));

        when(storagePackageOrderRepository.save(any(StoragePackageOrder.class))).thenAnswer(invocation -> {
            StoragePackageOrder o = invocation.getArgument(0);
            o.setId(10);
            return o;
        });

        when(storagePaymentService.createPayment(
                eq(10), anyLong(), eq(100000L), anyString(), eq("http://return"), eq("http://cancel")))
                .thenReturn("https://checkout.url");

        CreateStoragePackageOrderRequest request = new CreateStoragePackageOrderRequest();
        request.setPackageId(1);
        StoragePackageOrderResponseDTO dto = storagePackageService.createOrder(currentUser, request);

        assertNotNull(dto);
        assertEquals(Integer.valueOf(10), dto.getOrderId());
        assertEquals("https://checkout.url", dto.getCheckoutUrl());
        assertEquals("Gold package", dto.getPackageName());
        assertEquals(2000L, dto.getQuotaBytes());
        assertEquals(100000L, dto.getAmountVnd());
        assertEquals("PENDING", dto.getStatus());

        verify(storagePackageOrderRepository, Mockito.times(2)).save(any(StoragePackageOrder.class));
        verify(storagePaymentService).createPayment(
                eq(10), anyLong(), eq(100000L), anyString(), eq("http://return"), eq("http://cancel"));
    }

    @Test
    void testGetUsage_Success_QuotaGreaterThanZero() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);

        StorageUsageResponseDTO dto = storagePackageService.getUsage(currentUser);

        assertEquals(200L, dto.getUsedBytes());
        assertEquals(1000L, dto.getQuotaBytes());
        assertEquals(20.0, dto.getUsedPercentage());
    }

    @Test
    void testGetUsage_Success_QuotaIsZero() {
        company.setStorageQuotaBytes(0L);
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);

        StorageUsageResponseDTO dto = storagePackageService.getUsage(currentUser);

        assertEquals(200L, dto.getUsedBytes());
        assertEquals(0L, dto.getQuotaBytes());
        assertEquals(0.0, dto.getUsedPercentage());
    }
}
