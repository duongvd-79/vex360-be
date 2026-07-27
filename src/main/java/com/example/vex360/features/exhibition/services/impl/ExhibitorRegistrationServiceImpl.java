package com.example.vex360.features.exhibition.services.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.vex360.shared.dtos.PageResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.ExhibitorRegistrationService;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
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
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final PayOSIntegrationService payOSIntegrationService;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${app.payos.return-url:http://localhost:5175/payment/success}")
    private String returnUrl;

    @Value("${app.payos.cancel-url:http://localhost:5175/payment/cancel}")
    private String cancelUrl;

    private final Random random = new Random();

    @Override
    @Transactional
    public ExhibitorRegistration initializeRegistration(UUID companyUserId, Integer exhibitionPackageId,
            String participationReason) {
        User company = userService.getUserEntityByIdForUpdate(companyUserId);

        ExhibitionPackage expPackage = packageRepository.findById(exhibitionPackageId)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND));

        if (expPackage.getStatus() != ExhibitionPackageStatus.ACTIVE) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        Exhibition exhibition = expPackage.getExhibition();
        if (exhibition == null || (exhibition.getStatus() != ExhibitionStatus.REGISTRATION
                && exhibition.getStatus() != ExhibitionStatus.PUBLISHED
                && exhibition.getStatus() != ExhibitionStatus.ACTIVE)) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        boolean hasActiveRegistration = registrationRepository.existsActiveRegistration(
                companyUserId,
                exhibition.getId(),
                List.of(
                        ExhibitorRegistrationStatus.PENDING,
                        ExhibitorRegistrationStatus.PENDING_PAYMENT,
                        ExhibitorRegistrationStatus.APPROVED));
        if (hasActiveRegistration) {
            throw new AppException(ErrorCode.REGISTRATION_ALREADY_EXISTS);
        }

        PackageTemplate template = expPackage.getTemplate();

        // Create registration record in PENDING status with template snapshot values
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .company(company)
                .exhibitionPackage(expPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .participationReason(participationReason.trim())
                .packageNameSnapshot(template.getName())
                .priceSnapshot(template.getPrice())
                .finalPriceSnapshot(expPackage.getFinalPrice())
                .currencySnapshot(template.getCurrency())
                .maxProductsPerBoothSnapshot(template.getMaxProductsPerBooth())
                .maxEmbeddedVideosPerBoothSnapshot(template.getMaxEmbeddedVideosPerBooth())
                .maxPanoramasPerBoothSnapshot(template.getMaxPanoramasPerBooth())
                .maxHotspotsPerBoothSnapshot(template.getMaxHotspotsPerBooth())
                .storageLimitMbSnapshot(template.getStorageLimitMb())
                .listingPrioritySnapshot(template.getListingPriority())
                .build();
        return registrationRepository.save(registration);
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO getRegistrationDetails(UUID registrationUuid, UUID companyUserId) {
        ExhibitorRegistration registration = registrationRepository.findByUuidForUpdate(registrationUuid)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));

        if (!registration.getCompany().getId().equals(companyUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registration.getId())
                .orElse(null);

        if (payment != null && payment.getStatus() == PaymentStatus.PENDING
                && (payment.getCheckoutUrl() == null || payment.getCheckoutUrl().isBlank())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment = paymentRepository.save(payment);
        }

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
                newPayment.setStatus(PaymentStatus.FAILED);
                payment = paymentRepository.save(newPayment);
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
                organizer.getId(), exhibitionUuid, status,
                keyword == null || keyword.isBlank() ? null : keyword.trim(), pageable);

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
    @Transactional(readOnly = true)
    public PageResponse<ExhibitorRegistrationResponseDTO> getRegistrationsForExhibitor(
            User exhibitor, ExhibitorRegistrationStatus status, String keyword, Pageable pageable) {
        if (exhibitor == null || exhibitor.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<ExhibitorRegistration> page = registrationRepository.searchForExhibitor(
                exhibitor.getId(), status, normalizedKeyword, pageable);

        List<Integer> registrationIds = page.getContent().stream().map(ExhibitorRegistration::getId).toList();

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
            long orderCode = System.currentTimeMillis() / 1000 * 1000000L + random.nextLong(1000000L);
            Payment payment = Payment.builder()
                    .exhibitorRegistration(registration)
                    .orderCode(orderCode)
                    .amount(BigDecimal.ZERO)
                    .systemFee(BigDecimal.ZERO)
                    .organizerPayout(BigDecimal.ZERO)
                    .paymentProvider("FREE")
                    .status(PaymentStatus.PAID)
                    .paidAt(Instant.now())
                    .build();
            paymentRepository.save(payment);
            eventPublisher.publishEvent(new ExhibitorRegistrationApprovedEvent(this, registration));
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

        String normalizedReason = rejectedReason == null ? null : rejectedReason.trim();
        if (normalizedReason == null || normalizedReason.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        registration.setStatus(ExhibitorRegistrationStatus.REJECTED);
        registration.setReviewedBy(organizer);
        registration.setRejectedReason(normalizedReason);
        registration = registrationRepository.save(registration);

        Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registration.getId())
                .orElse(null);

        return mapToResponse(registration, payment);
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO cancelRegistration(User exhibitor, UUID registrationUuid) {
        if (exhibitor == null || exhibitor.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        ExhibitorRegistration registration = registrationRepository.findByUuidForUpdate(registrationUuid)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));

        if (!registration.getCompany().getId().equals(exhibitor.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (registration.getStatus() != ExhibitorRegistrationStatus.PENDING
                && registration.getStatus() != ExhibitorRegistrationStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        registration.setStatus(ExhibitorRegistrationStatus.CANCELLED);
        registration = registrationRepository.save(registration);

        List<Payment> pendingPayments = paymentRepository
                .findByExhibitorRegistrationIdForUpdate(registration.getId());
        for (Payment p : pendingPayments) {
            if (p.getStatus() == PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(p);
            }
        }

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
                .packageName(registration.getPackageNameSnapshot() != null ? registration.getPackageNameSnapshot()
                        : registration.getExhibitionPackage().getTemplate().getName())
                .priceSnapshot(registration.getPriceSnapshot())
                .finalPriceSnapshot(registration.getFinalPriceSnapshot())
                .currencySnapshot(registration.getCurrencySnapshot())
                .exhibitionName(registration.getExhibitionPackage().getExhibition().getName())
                .participationReason(registration.getParticipationReason())
                .rejectedReason(registration.getRejectedReason())
                .reviewedByName(
                        registration.getReviewedBy() != null ? registration.getReviewedBy().getFullName() : null)
                .build();
    }
}
