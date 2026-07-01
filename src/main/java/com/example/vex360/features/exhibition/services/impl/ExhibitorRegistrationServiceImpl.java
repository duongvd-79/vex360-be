package com.example.vex360.features.exhibition.services.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.ExhibitorRegistrationService;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.entities.ExhibitionPackage;
import com.example.vex360.shared.entities.ExhibitorRegistration;
import com.example.vex360.shared.entities.Payment;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitorRegistrationServiceImpl implements ExhibitorRegistrationService {

    private final ExhibitorRegistrationRepository registrationRepository;
    private final ExhibitionPackageRepository packageRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PayOSIntegrationService payOSIntegrationService;

    @Value("${app.payos.return-url:http://localhost:5175/payment/success}")
    private String returnUrl;

    @Value("${app.payos.cancel-url:http://localhost:5175/payment/cancel}")
    private String cancelUrl;

    @Override
    @Transactional
    public ExhibitorRegistration initializeRegistration(UUID companyUserId, Integer exhibitionPackageId) {
        User company = userRepository.findById(companyUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ExhibitionPackage expPackage = packageRepository.findById(exhibitionPackageId)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND));

        BigDecimal finalPrice = expPackage.getFinalPrice();

        // Under Option B, Platform fees are charged to the Organizer, not the Exhibitor.
        // Exhibitor only pays finalPrice (which can be 0).
        BigDecimal systemFee = BigDecimal.ZERO;
        BigDecimal organizerPayout = finalPrice;

        // 1. Create registration record
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .company(company)
                .exhibitionPackage(expPackage)
                .status(finalPrice.compareTo(BigDecimal.ZERO) == 0 ? 
                        ExhibitorRegistrationStatus.APPROVED : ExhibitorRegistrationStatus.PENDING)
                .build();
        registration = registrationRepository.save(registration);

        // Generate unique orderCode
        long orderCode = System.currentTimeMillis() / 1000 * 1000000L + (long) (Math.random() * 1000000L);

        if (finalPrice.compareTo(BigDecimal.ZERO) == 0) {
            // Free Package Path
            Payment payment = Payment.builder()
                    .exhibitorRegistration(registration)
                    .orderCode(orderCode)
                    .amount(BigDecimal.ZERO)
                    .systemFee(BigDecimal.ZERO)
                    .organizerPayout(BigDecimal.ZERO)
                    .paymentProvider("FREE")
                    .status(PaymentStatus.PAID)
                    .paidAt(LocalDateTime.now())
                    .build();
            paymentRepository.save(payment);
            log.info("Initialized Free registration (ID: {}) and marked as APPROVED", registration.getId());
        } else {
            // Paid Package Path (requires PayOS)
            Payment payment = Payment.builder()
                    .exhibitorRegistration(registration)
                    .orderCode(orderCode)
                    .amount(finalPrice)
                    .systemFee(systemFee)
                    .organizerPayout(organizerPayout)
                    .paymentProvider("PAYOS")
                    .status(PaymentStatus.PENDING)
                    .build();
            payment = paymentRepository.save(payment);

            String description = "Dang ky trien lam " + expPackage.getExhibition().getName();
            // PayOS description has length limit, keep it short
            if (description.length() > 25) {
                description = description.substring(0, 25);
            }

            CreatePaymentLinkResponse response = payOSIntegrationService.createPaymentLink(
                    orderCode,
                    finalPrice.longValue(),
                    description,
                    returnUrl,
                    cancelUrl
            );

            payment.setCheckoutUrl(response.getCheckoutUrl());
            paymentRepository.save(payment);
            log.info("Initialized Paid registration (ID: {}) with PayOS link: {}", registration.getId(), response.getCheckoutUrl());
        }

        return registration;
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitorRegistrationResponseDTO getRegistrationDetails(UUID registrationUuid, UUID companyUserId) {
        ExhibitorRegistration registration = registrationRepository.findByUuid(registrationUuid)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));

        if (!registration.getCompany().getId().equals(companyUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registration.getId())
                .orElse(null);

        return mapToResponse(registration, payment);
    }

    private ExhibitorRegistrationResponseDTO mapToResponse(ExhibitorRegistration registration, Payment payment) {
        return ExhibitorRegistrationResponseDTO.builder()
                .uuid(registration.getUuid())
                .exhibitionPackageId(registration.getExhibitionPackage().getId())
                .companyUserId(registration.getCompany().getId())
                .status(registration.getStatus().name())
                .submittedAt(registration.getSubmittedAt())
                .checkoutUrl(payment != null ? payment.getCheckoutUrl() : null)
                .paymentStatus(payment != null ? payment.getStatus().name() : null)
                .orderCode(payment != null ? payment.getOrderCode() : null)
                .build();
    }
}
