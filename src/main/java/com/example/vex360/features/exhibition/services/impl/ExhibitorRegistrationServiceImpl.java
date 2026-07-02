package com.example.vex360.features.exhibition.services.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.vex360.shared.dtos.PageResponse;

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

    private final Random random = new Random();

    @Override
    @Transactional
    public ExhibitorRegistration initializeRegistration(UUID companyUserId, Integer exhibitionPackageId) {
        User company = userRepository.findById(companyUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ExhibitionPackage expPackage = packageRepository.findById(exhibitionPackageId)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND));

        // Create registration record in PENDING status (requires Organizer approval
        // first)
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .company(company)
                .exhibitionPackage(expPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();
        return registrationRepository.save(registration);
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO getRegistrationDetails(UUID registrationUuid, UUID companyUserId) {
        ExhibitorRegistration registration = registrationRepository.findByUuid(registrationUuid)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));

        if (!registration.getCompany().getId().equals(companyUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registration.getId())
                .orElse(null);

        // If the registration is approved and waiting for payment, generate PayOS
        // checkout URL on the fly if needed
        if (registration.getStatus() == ExhibitorRegistrationStatus.PENDING_PAYMENT
                && (payment == null || payment.getStatus() == PaymentStatus.FAILED)) {
            // Generate a new payment link
            BigDecimal finalPrice = registration.getExhibitionPackage().getFinalPrice();
            long orderCode = System.currentTimeMillis() / 1000 * 1000000L + this.random.nextLong(1000000L);

            Payment newPayment = Payment.builder()
                    .exhibitorRegistration(registration)
                    .orderCode(orderCode)
                    .amount(finalPrice)
                    .systemFee(BigDecimal.ZERO)
                    .organizerPayout(finalPrice)
                    .paymentProvider("PAYOS")
                    .status(PaymentStatus.PENDING)
                    .build();
            newPayment = paymentRepository.save(newPayment);

            String description = "Dang ky trien lam "
                    + registration.getExhibitionPackage().getExhibition().getName();
            if (description.length() > 25) {
                description = description.substring(0, 25);
            }

            try {
                CreatePaymentLinkResponse response = payOSIntegrationService.createPaymentLink(
                        orderCode,
                        finalPrice.longValue(),
                        description,
                        returnUrl,
                        cancelUrl);
                newPayment.setCheckoutUrl(response.getCheckoutUrl());
                payment = paymentRepository.save(newPayment);
                log.info("Automatically generated PayOS link for registration {}: {}", registration.getId(),
                        response.getCheckoutUrl());
            } catch (Exception e) {
                log.error("Failed to generate PayOS link for registration {}", registration.getId(), e);
            }
        }

        return mapToResponse(registration, payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitorRegistrationResponseDTO> getRegistrationsForOrganizer(
            User organizer, UUID exhibitionUuid, ExhibitorRegistrationStatus status, String keyword,
            Pageable pageable) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Page<ExhibitorRegistration> page = registrationRepository.searchForOrganizer(
                organizer.getId(), exhibitionUuid, status, keyword, pageable);

        List<Integer> registrationIds = page.getContent().stream().map(ExhibitorRegistration::getId).toList();

        // High Performance: Fetch all associated payments in ONE batch query (anti-N+1
        // query pattern)
        Map<Integer, Payment> paymentMap = new HashMap<>();
        if (!registrationIds.isEmpty()) {
            List<Payment> payments = paymentRepository.findByExhibitorRegistrationIdIn(registrationIds);
            for (Payment p : payments) {
                Payment existing = paymentMap.get(p.getExhibitorRegistration().getId());
                if (existing == null || p.getCreatedAt().isAfter(existing.getCreatedAt())) {
                    paymentMap.put(p.getExhibitorRegistration().getId(), p);
                }
            }
        }

        List<ExhibitorRegistrationResponseDTO> dtoList = page.getContent().stream()
                .map(r -> mapToResponse(r, paymentMap.get(r.getId())))
                .toList();

        return PageResponse.<ExhibitorRegistrationResponseDTO>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .first(page.isFirst())
                .last(page.isLast())
                .content(dtoList)
                .build();
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO approveRegistration(User organizer, UUID registrationUuid) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        ExhibitorRegistration registration = registrationRepository.findByUuid(registrationUuid)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));

        // Check ownership
        if (!registration.getExhibitionPackage().getExhibition().getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (registration.getStatus() != ExhibitorRegistrationStatus.PENDING) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        BigDecimal finalPrice = registration.getExhibitionPackage().getFinalPrice();
        if (finalPrice.compareTo(BigDecimal.ZERO) == 0) {
            // Free package: direct approve
            registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
        } else {
            // Paid package: set to PENDING_PAYMENT
            registration.setStatus(ExhibitorRegistrationStatus.PENDING_PAYMENT);
        }
        registration.setReviewedBy(organizer);
        registration.setRejectedReason(null);
        registration = registrationRepository.save(registration);

        // If it was auto-approved (free), also create a FREE payment record for
        // tracking
        if (registration.getStatus() == ExhibitorRegistrationStatus.APPROVED) {
            long orderCode = System.currentTimeMillis() / 1000 * 1000000L + (long) (Math.random() * 1000000L);
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
        }

        Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registration.getId())
                .orElse(null);

        return mapToResponse(registration, payment);
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO rejectRegistration(User organizer, UUID registrationUuid,
            String rejectedReason) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        ExhibitorRegistration registration = registrationRepository.findByUuid(registrationUuid)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));

        // Check ownership
        if (!registration.getExhibitionPackage().getExhibition().getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (registration.getStatus() != ExhibitorRegistrationStatus.PENDING
                && registration.getStatus() != ExhibitorRegistrationStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        registration.setStatus(ExhibitorRegistrationStatus.REJECTED);
        registration.setReviewedBy(organizer);
        registration.setRejectedReason(rejectedReason);
        registration = registrationRepository.save(registration);

        Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registration.getId())
                .orElse(null);

        return mapToResponse(registration, payment);
    }

    private ExhibitorRegistrationResponseDTO mapToResponse(ExhibitorRegistration registration, Payment payment) {
        return ExhibitorRegistrationResponseDTO.builder()
                .id(registration.getId())
                .uuid(registration.getUuid())
                .exhibitionPackageId(registration.getExhibitionPackage().getId())
                .companyUserId(registration.getCompany().getId())
                .status(registration.getStatus().name())
                .submittedAt(registration.getSubmittedAt())
                .checkoutUrl(payment != null ? payment.getCheckoutUrl() : null)
                .paymentStatus(payment != null ? payment.getStatus().name() : null)
                .orderCode(payment != null ? payment.getOrderCode() : null)
                .companyName(registration.getCompany().getFullName())
                .companyEmail(registration.getCompany().getEmail())
                .packageName(registration.getExhibitionPackage().getTemplate().getName())
                .exhibitionName(registration.getExhibitionPackage().getExhibition().getName())
                .rejectedReason(registration.getRejectedReason())
                .reviewedByName(
                        registration.getReviewedBy() != null ? registration.getReviewedBy().getFullName() : null)
                .build();
    }
}
