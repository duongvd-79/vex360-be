package com.example.vex360.features.exhibition.services.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.vex360.shared.dtos.PageResponse;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.ExhibitorRegistrationService;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.events.ExhibitionPaymentCompletedEvent;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import jakarta.persistence.EntityNotFoundException;

import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitorRegistrationServiceImpl implements ExhibitorRegistrationService {
    private static final long RESERVATION_TTL_HOURS = 24L;

    private final ExhibitorRegistrationRepository registrationRepository;
    private final ExhibitionPackageRepository packageRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final UserService userService;
    private final CompanyService companyService;
    private final PaymentRepository paymentRepository;
    private final PayOSIntegrationService payOSIntegrationService;
    private final PaymentFulfillmentService paymentFulfillmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExhibitionTimelinePolicy timelinePolicy;
    private final MailService mailService;
    private final AfterCommitExecutor afterCommitExecutor;
    @Value("${app.payos.return-url:http://localhost:5175/payment/success}")
    private String returnUrl;

    @Value("${app.payos.cancel-url:http://localhost:5175/payment/cancel}")
    private String cancelUrl;

    private final Random random = new Random();

    @Override
    @Transactional
    public ExhibitorRegistration initializeRegistration(UUID companyUserId, Integer exhibitionPackageId,
            String participationReason, String boothName, String boothDescription) {
        User exhibitorUser = userService.getUserEntityById(companyUserId);
        Company company = companyService.getCompanyEntityForCurrentUserForUpdate(exhibitorUser);

        ExhibitionPackage expPackage = packageRepository.findById(exhibitionPackageId)
                .orElseThrow(() -> {
                    log.error("Exhibition package not found for ID: {}", exhibitionPackageId);
                    return new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND);
                });

        if (expPackage.getStatus() != ExhibitionPackageStatus.ACTIVE) {
            log.error("Exhibition package {} is not active (status: {})", exhibitionPackageId, expPackage.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_PACKAGE_INACTIVE);
        }

        Exhibition packageExhibition = expPackage.getExhibition();
        if (packageExhibition == null) {
            log.error("Exhibition package {} has no associated exhibition", exhibitionPackageId);
            throw new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND);
        }

        Exhibition exhibition = exhibitionRepository.findByIdForUpdate(packageExhibition.getId())
                .orElseThrow(() -> {
                    log.error("Exhibition not found for ID: {}", packageExhibition.getId());
                    return new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND);
                });
        if (!timelinePolicy.isRegistrationOpen(exhibition)) {
            log.error("Registration is closed for exhibition {}", exhibition.getId());
            throw new AppException(ErrorCode.REGISTRATION_CLOSED);
        }

        boolean hasActiveRegistration = registrationRepository.existsActiveRegistration(
                company.getId(),
                exhibition.getId(),
                List.of(
                        ExhibitorRegistrationStatus.PENDING,
                        ExhibitorRegistrationStatus.PENDING_PAYMENT,
                        ExhibitorRegistrationStatus.APPROVED));
        if (hasActiveRegistration) {
            log.error("Company {} already has an active registration for exhibition {}", company.getId(),
                    exhibition.getId());
            throw new AppException(ErrorCode.REGISTRATION_ALREADY_EXISTS);
        }
        // Create registration record in PENDING status with the exhibition package
        // terms.
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .company(company)
                .exhibitionPackage(expPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .participationReason(participationReason.trim())
                .boothName(boothName.trim())
                .boothDescription(boothDescription.trim())
                .packageNameSnapshot(expPackage.getPackageNameSnapshot())
                .priceSnapshot(expPackage.getPriceSnapshot())
                .finalPriceSnapshot(expPackage.getFinalPrice())
                .currencySnapshot(expPackage.getCurrencySnapshot())
                .maxProductsPerBoothSnapshot(expPackage.getMaxProductsPerBoothSnapshot())
                .maxEmbeddedVideosPerBoothSnapshot(expPackage.getMaxEmbeddedVideosPerBoothSnapshot())
                .maxPanoramasPerBoothSnapshot(expPackage.getMaxPanoramasPerBoothSnapshot())
                .maxHotspotsPerBoothSnapshot(expPackage.getMaxHotspotsPerBoothSnapshot())
                .listingPrioritySnapshot(expPackage.getListingPrioritySnapshot())
                .build();
        return registrationRepository.save(registration);
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO getRegistrationDetails(UUID registrationUuid, UUID companyUserId) {
        User exhibitorUser = userService.getUserEntityById(companyUserId);
        Company company = companyService.getCompanyEntityForCurrentUser(exhibitorUser);

        ExhibitorRegistration registration = registrationRepository.findByUuidForUpdate(registrationUuid)
                .orElseThrow(() -> {
                    log.error("Exhibitor registration not found for UUID: {}", registrationUuid);
                    return new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
                });

        if (registration.getCompany() == null || registration.getCompany().getId() == null) {
            log.error("[PB-001/002] Pre-payment dependency check failed: company missing for registration UUID {}",
                    registrationUuid);
            throw new AppException(ErrorCode.REGISTRATION_COMPANY_MISSING);
        }

        if (!registration.getCompany().getId().equals(company.getId())) {
            log.error("Company {} is not authorized to access registration {}", company.getId(), registrationUuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        validateRegistrationDependencies(registration);

        Payment payment = ensureValidPaymentForRegistration(registration);
        return mapToResponse(registration, payment);
    }

    private Payment ensureValidPaymentForRegistration(ExhibitorRegistration registration) {
        if (registration == null) {
            return null;
        }

        Payment payment = paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(registration.getId())
                .orElse(null);

        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            BigDecimal finalPrice = getFinalPrice(registration);
            BigDecimal systemFee = getBasePrice(registration);
            BigDecimal organizerPayout = finalPrice.subtract(systemFee);
            if (!finalPrice.equals(payment.getAmount())
                    || !systemFee.equals(payment.getSystemFee())
                    || !organizerPayout.equals(payment.getOrganizerPayout())) {
                payment.setAmount(finalPrice);
                payment.setSystemFee(systemFee);
                payment.setOrganizerPayout(organizerPayout);
                paymentRepository.save(payment);
            }
            if (payment.getOrderCode() != null) {
                PaymentLink linkData = null;
                try {
                    linkData = payOSIntegrationService.getPaymentLinkInformation(payment.getOrderCode());
                } catch (Exception e) {
                    log.warn("Failed to check PayOS link info for orderCode {}, preserving PENDING payment: {}",
                            payment.getOrderCode(), e.getMessage());
                }

                if (linkData != null && linkData.getStatus() == PaymentLinkStatus.PAID) {
                    payment.setStatus(PaymentStatus.PAID);
                    payment.setPaidAt(Instant.now());
                    payment = paymentRepository.save(payment);
                    paymentFulfillmentService.processFulfillmentForOrderCode(payment.getOrderCode());
                    eventPublisher.publishEvent(new ExhibitionPaymentCompletedEvent(this, payment));
                } else if (linkData != null
                        && (linkData.getStatus() == PaymentLinkStatus.CANCELLED
                                || linkData.getStatus() == PaymentLinkStatus.EXPIRED)) {
                    log.info("PayOS link for orderCode {} has status {}, marking payment as FAILED",
                            payment.getOrderCode(), linkData.getStatus());
                    payment.setStatus(PaymentStatus.FAILED);
                    payment = paymentRepository.save(payment);
                }
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment = paymentRepository.save(payment);
            }
        }

        if (registration.getStatus() == ExhibitorRegistrationStatus.PENDING_PAYMENT
                && (payment == null || payment.getStatus() == PaymentStatus.FAILED)) {
            validateRegistrationDependencies(registration);

            if (!timelinePolicy.isRegistrationOpen(registration.getExhibitionPackage().getExhibition())) {
                log.warn(
                        "Registration closed for exhibition, refusing to generate new payment link for registration {}",
                        registration.getId());
                throw new AppException(ErrorCode.REGISTRATION_CLOSED);
            }

            BigDecimal finalPrice = getFinalPrice(registration);
            long orderCode = System.currentTimeMillis() / 1000 * 1000000L + this.random.nextLong(1000000L);

            BigDecimal systemFee = getBasePrice(registration);

            Payment newPayment = Payment.builder()
                    .exhibitorRegistration(registration)
                    .orderCode(orderCode)
                    .amount(finalPrice)
                    .systemFee(systemFee)
                    .organizerPayout(finalPrice.subtract(systemFee))
                    .paymentProvider("PAYOS")
                    .status(PaymentStatus.PENDING)
                    .build();
            newPayment = paymentRepository.save(newPayment);

            String description = "Dang ky trien lam "
                    + registration.getExhibitionPackage().getExhibition().getName();
            if (description.length() > 25) {
                description = description.substring(0, 25);
            }

            CreatePaymentLinkResponse response;
            try {
                response = payOSIntegrationService.createPaymentLink(
                        orderCode,
                        finalPrice.longValue(),
                        description,
                        returnUrl,
                        cancelUrl);
            } catch (Exception e) {
                log.error("Failed to generate PayOS link for registration {}", registration.getId(), e);
                return newPayment;
            }

            newPayment.setCheckoutUrl(response.getCheckoutUrl());
            payment = paymentRepository.save(newPayment);
            log.info("Automatically generated PayOS link for registration {}: {}", registration.getId(),
                    newPayment.getCheckoutUrl());
        }

        return payment;
    }

    private BigDecimal getFinalPrice(ExhibitorRegistration registration) {
        return registration.getFinalPriceSnapshot() != null
                ? registration.getFinalPriceSnapshot()
                : registration.getExhibitionPackage().getFinalPrice();
    }

    private BigDecimal getBasePrice(ExhibitorRegistration registration) {
        BigDecimal basePrice = registration.getPriceSnapshot() != null
                ? registration.getPriceSnapshot()
                : registration.getExhibitionPackage().getPriceSnapshot();
        return basePrice == null ? BigDecimal.ZERO : basePrice;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitorRegistrationResponseDTO> getRegistrationsForOrganizer(
            User organizer, UUID exhibitionUuid, ExhibitorRegistrationStatus status, String keyword,
            Pageable pageable) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Page<ExhibitorRegistration> page = registrationRepository.searchForOrganizer(
                organizer.getId(), exhibitionUuid, status,
                keyword == null || keyword.isBlank() ? null : keyword.trim(), pageable);

        return mapToPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitorRegistrationResponseDTO> getRegistrationsForExhibitor(
            User exhibitor, ExhibitorRegistrationStatus status, String keyword, Pageable pageable) {
        if (exhibitor == null || exhibitor.getId() == null) {
            log.error("Exhibitor authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Company company = companyService.getCompanyEntityForCurrentUser(exhibitor);

        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<ExhibitorRegistration> page = registrationRepository.searchForExhibitor(
                company.getId(), status, normalizedKeyword, pageable);

        return mapToPageResponse(page);
    }

    private PageResponse<ExhibitorRegistrationResponseDTO> mapToPageResponse(Page<ExhibitorRegistration> page) {
        List<Integer> registrationIds = page.getContent().stream().map(ExhibitorRegistration::getId).toList();

        Map<Integer, Payment> paymentMap = new HashMap<>();
        if (!registrationIds.isEmpty()) {
            List<Payment> payments = paymentRepository.findLatestPaymentsByRegistrationIds(registrationIds);
            for (Payment p : payments) {
                paymentMap.put(p.getExhibitorRegistration().getId(), p);
            }
        }

        return PageResponse.from(page.map(r -> mapToResponse(r, paymentMap.get(r.getId()))));
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO approveRegistration(User organizer, UUID registrationUuid) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        ExhibitorRegistration registration = registrationRepository.findByUuidForUpdate(registrationUuid)
                .orElseThrow(() -> {
                    log.error("Registration not found for UUID: {}", registrationUuid);
                    return new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
                });

        // Check ownership
        Exhibition exp = registration.getExhibitionPackage() != null
                ? registration.getExhibitionPackage().getExhibition()
                : null;
        User expOrganizer = exp != null ? exp.getOrganizer() : null;
        if (expOrganizer == null || expOrganizer.getId() == null || !expOrganizer.getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized to approve registration {}", organizer.getId(), registrationUuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (registration.getStatus() != ExhibitorRegistrationStatus.PENDING) {
            log.error("Cannot approve registration {} with status {}", registrationUuid, registration.getStatus());
            throw new AppException(ErrorCode.REGISTRATION_INVALID_STATUS);
        }

        if (!timelinePolicy.isRegistrationOpen(exp)) {
            throw new AppException(ErrorCode.REGISTRATION_CLOSED);
        }

        ExhibitionPackage pkg = registration.getExhibitionPackage();
        if (pkg != null && pkg.getMaxBooths() != null) {
            long activeAndReservedCount = registrationRepository.countActiveAndReservedByPackageId(pkg.getId(),
                    Instant.now());
            if (activeAndReservedCount >= pkg.getMaxBooths()) {
                throw new AppException(ErrorCode.EXHIBITION_PACKAGE_FULL);
            }
        }

        BigDecimal finalPrice = registration.getFinalPriceSnapshot() != null
                ? registration.getFinalPriceSnapshot()
                : (pkg != null ? pkg.getFinalPrice() : BigDecimal.ZERO);
        if (finalPrice.compareTo(BigDecimal.ZERO) == 0 && pkg != null && pkg.getFinalPrice() != null
                && pkg.getFinalPrice().compareTo(BigDecimal.ZERO) > 0) {
            finalPrice = pkg.getFinalPrice();
            registration.setFinalPriceSnapshot(finalPrice);
        }
        if (finalPrice.compareTo(BigDecimal.ZERO) == 0) {
            // Free package: direct approve
            registration.setStatus(ExhibitorRegistrationStatus.APPROVED);
            registration.setReservedUntil(null);
        } else {
            // Paid package: set to PENDING_PAYMENT with reservation TTL
            registration.setStatus(ExhibitorRegistrationStatus.PENDING_PAYMENT);
            registration.setReservedUntil(Instant.now().plus(RESERVATION_TTL_HOURS, ChronoUnit.HOURS));
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

        if (registration.getStatus() == ExhibitorRegistrationStatus.PENDING_PAYMENT) {
            sendExhibitorRegistrationReviewMailSafely(registration, ExhibitorRegistrationStatus.PENDING_PAYMENT, null);
        }

        return mapToResponse(registration, payment);
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO rejectRegistration(User organizer, UUID registrationUuid,
            String rejectedReason) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        ExhibitorRegistration registration = registrationRepository.findByUuidForUpdate(registrationUuid)
                .orElseThrow(() -> {
                    log.error("Registration not found for UUID: {}", registrationUuid);
                    return new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
                });

        // Check ownership
        if (!registration.getExhibitionPackage().getExhibition().getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized to reject registration {}", organizer.getId(), registrationUuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (registration.getStatus() != ExhibitorRegistrationStatus.PENDING
                && registration.getStatus() != ExhibitorRegistrationStatus.PENDING_PAYMENT) {
            log.error("Cannot reject registration {} with status {}", registrationUuid, registration.getStatus());
            throw new AppException(ErrorCode.REGISTRATION_INVALID_STATUS);
        }

        String normalizedReason = rejectedReason == null ? null : rejectedReason.trim();
        if (normalizedReason == null || normalizedReason.isEmpty()) {
            log.error("Rejection reason is required for rejecting registration {}", registrationUuid);
            throw new AppException(ErrorCode.REGISTRATION_REJECTION_REASON_REQUIRED);
        }

        registration.setStatus(ExhibitorRegistrationStatus.REJECTED);
        registration.setReviewedBy(organizer);
        registration.setRejectedReason(normalizedReason);
        registration = registrationRepository.save(registration);

        List<Payment> pendingPayments = paymentRepository
                .findByExhibitorRegistrationIdForUpdate(registration.getId());
        for (Payment p : pendingPayments) {
            if (p.getStatus() == PaymentStatus.PENDING) {
                if (p.getOrderCode() != null) {
                    try {
                        payOSIntegrationService.cancelPaymentLink(p.getOrderCode(), "Organizer rejected registration");
                    } catch (Exception e) {
                        log.warn("Failed to cancel PayOS link for orderCode {}: {}", p.getOrderCode(), e.getMessage());
                    }
                }
                p.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(p);
            }
        }

        Payment payment = pendingPayments.isEmpty() ? null : pendingPayments.get(0);

        sendExhibitorRegistrationReviewMailSafely(registration, ExhibitorRegistrationStatus.REJECTED, normalizedReason);

        return mapToResponse(registration, payment);
    }

    private void sendExhibitorRegistrationReviewMailSafely(
            ExhibitorRegistration registration,
            ExhibitorRegistrationStatus result,
            String rejectedReason) {
        try {
            Company company = registration.getCompany();
            User recipient = company != null ? company.getOwnerUser() : null;
            if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                log.warn(
                        "Cannot send exhibitor registration review email for registration ID {}: recipient or email missing",
                        registration.getId());
                return;
            }
            ExhibitionPackage pkg = registration.getExhibitionPackage();
            Exhibition exhibition = pkg != null ? pkg.getExhibition() : null;
            String recipientEmail = recipient.getEmail();
            String recipientFullName = recipient.getFullName();
            String exhibitionName = exhibition != null ? exhibition.getName() : "";
            String companyName = company.getName();
            String packageName = registration.getPackageNameSnapshot() != null
                    && !registration.getPackageNameSnapshot().isBlank()
                            ? registration.getPackageNameSnapshot()
                            : (pkg != null ? pkg.getPackageNameSnapshot() : "");
            BigDecimal finalPrice = registration.getFinalPriceSnapshot() != null
                    ? registration.getFinalPriceSnapshot()
                    : (pkg != null ? pkg.getFinalPrice() : null);
            String currency = registration.getCurrencySnapshot() != null
                    && !registration.getCurrencySnapshot().isBlank()
                            ? registration.getCurrencySnapshot()
                            : "VND";

            afterCommitExecutor.execute(() -> mailService.sendExhibitorRegistrationReviewResultEmail(
                    recipientEmail,
                    recipientFullName,
                    companyName,
                    exhibitionName,
                    packageName,
                    finalPrice,
                    currency,
                    result,
                    rejectedReason));
        } catch (Exception e) {
            log.error("Failed to trigger exhibitor registration review email for registration ID {}: {}",
                    registration.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public ExhibitorRegistrationResponseDTO cancelRegistration(User exhibitor, UUID registrationUuid) {
        if (exhibitor == null || exhibitor.getId() == null) {
            log.error("Exhibitor authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Company company = companyService.getCompanyEntityForCurrentUser(exhibitor);

        ExhibitorRegistration registration = registrationRepository.findByUuidForUpdate(registrationUuid)
                .orElseThrow(() -> {
                    log.error("Registration not found for UUID: {}", registrationUuid);
                    return new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
                });

        if (registration.getCompany() == null || registration.getCompany().getId() == null) {
            log.error("Company missing for registration UUID {}", registrationUuid);
            throw new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID);
        }

        if (!registration.getCompany().getId().equals(company.getId())) {
            log.error("Company {} is not authorized to cancel registration {}", company.getId(), registrationUuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (registration.getStatus() != ExhibitorRegistrationStatus.PENDING
                && registration.getStatus() != ExhibitorRegistrationStatus.PENDING_PAYMENT) {
            log.error("Cannot cancel registration {} with status {}", registrationUuid, registration.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_CANNOT_CANCEL);
        }

        List<Payment> payments = paymentRepository
                .findByExhibitorRegistrationIdForUpdate(registration.getId());
        boolean hasPaidPayment = payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PAID);
        if (hasPaidPayment) {
            log.warn("Cannot cancel registration {} because payment is already PAID", registrationUuid);
            throw new AppException(ErrorCode.REGISTRATION_ALREADY_PAID);
        }

        registration.setStatus(ExhibitorRegistrationStatus.CANCELED);
        registration = registrationRepository.save(registration);

        for (Payment p : payments) {
            if (p.getStatus() == PaymentStatus.PENDING) {
                if (p.getOrderCode() != null) {
                    try {
                        payOSIntegrationService.cancelPaymentLink(p.getOrderCode(), "Exhibitor canceled registration");
                    } catch (Exception e) {
                        log.warn("Failed to cancel PayOS link for orderCode {}: {}", p.getOrderCode(), e.getMessage());
                    }
                }
                p.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(p);
            }
        }

        Payment payment = payments.isEmpty() ? null : payments.get(0);

        return mapToResponse(registration, payment);
    }

    private ExhibitorRegistrationResponseDTO mapToResponse(ExhibitorRegistration registration, Payment payment) {
        User owner = registration.getCompany().getOwnerUser();
        return ExhibitorRegistrationResponseDTO.builder()
                .id(registration.getId())
                .uuid(registration.getUuid())
                .exhibitionPackageId(registration.getExhibitionPackage().getId())
                .companyUserId(owner != null ? owner.getId() : null)
                .status(registration.getStatus().name())
                .submittedAt(registration.getSubmittedAt())
                .reservedUntil(registration.getReservedUntil())
                .checkoutUrl(payment != null ? payment.getCheckoutUrl() : null)
                .paymentStatus(payment != null ? payment.getStatus().name() : null)
                .orderCode(payment != null ? payment.getOrderCode() : null)
                .companyName(registration.getCompany().getName())
                .companyEmail(owner != null ? owner.getEmail() : null)
                .packageName(registration.getPackageNameSnapshot() != null ? registration.getPackageNameSnapshot()
                        : registration.getExhibitionPackage().getPackageNameSnapshot())
                .priceSnapshot(registration.getPriceSnapshot())
                .finalPriceSnapshot(registration.getFinalPriceSnapshot())
                .currencySnapshot(registration.getCurrencySnapshot())
                .maxProductsPerBoothSnapshot(registration.getMaxProductsPerBoothSnapshot())
                .maxEmbeddedVideosPerBoothSnapshot(registration.getMaxEmbeddedVideosPerBoothSnapshot())
                .maxPanoramasPerBoothSnapshot(registration.getMaxPanoramasPerBoothSnapshot())
                .maxHotspotsPerBoothSnapshot(registration.getMaxHotspotsPerBoothSnapshot())
                .exhibitionName(registration.getExhibitionPackage().getExhibition().getName())
                .participationReason(registration.getParticipationReason())
                .boothName(registration.getBoothName())
                .boothDescription(registration.getBoothDescription())
                .rejectedReason(registration.getRejectedReason())
                .reviewedByName(
                        registration.getReviewedBy() != null ? registration.getReviewedBy().getFullName() : null)
                .build();
    }

    private void validateRegistrationDependencies(ExhibitorRegistration registration) {
        if (registration == null) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
        }
        try {
            if (registration.getCompany() == null || registration.getCompany().getId() == null) {
                log.error("[PB-001/002] Pre-payment dependency check failed: company missing for registration UUID {}",
                        registration.getUuid());
                throw new AppException(ErrorCode.REGISTRATION_COMPANY_MISSING);
            }
            if (registration.getCompany().getOwnerUser() == null
                    || registration.getCompany().getOwnerUser().getId() == null) {
                log.error(
                        "[PB-001/002] Pre-payment dependency check failed: company owner user missing for registration UUID {}",
                        registration.getUuid());
                throw new AppException(ErrorCode.REGISTRATION_COMPANY_OWNER_MISSING);
            }
            if (registration.getExhibitionPackage() == null || registration.getExhibitionPackage().getId() == null) {
                log.error(
                        "[PB-001/002] Pre-payment dependency check failed: exhibition package missing for registration UUID {}",
                        registration.getUuid());
                throw new AppException(ErrorCode.REGISTRATION_PACKAGE_MISSING);
            }
            if (registration.getExhibitionPackage().getExhibition() == null
                    || registration.getExhibitionPackage().getExhibition().getId() == null) {
                log.error(
                        "[PB-001/002] Pre-payment dependency check failed: exhibition missing for registration UUID {}",
                        registration.getUuid());
                throw new AppException(ErrorCode.REGISTRATION_EXHIBITION_MISSING);
            }
        } catch (EntityNotFoundException | ObjectNotFoundException e) {
            log.error("[PB-001/002] Pre-payment dependency check threw exception for registration UUID {}",
                    registration.getUuid(), e);
            throw new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExhibitorRegistration> findRegistrationWithRelationsById(Integer registrationId) {
        if (registrationId == null) {
            return Optional.empty();
        }
        return registrationRepository.findByIdWithRelations(registrationId);
    }
}
