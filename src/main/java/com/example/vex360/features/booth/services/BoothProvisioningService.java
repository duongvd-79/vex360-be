package com.example.vex360.features.booth.services;

import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.services.ExhibitorRegistrationService;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoothProvisioningService {
    private final BoothRepository boothRepository;
    private final ExhibitorRegistrationService exhibitorRegistrationService;

    @Transactional
    public Optional<Booth> ensureBoothForApprovedRegistration(ExhibitorRegistration registration) {
        if (registration == null || registration.getId() == null) {
            return Optional.empty();
        }
        return ensureBoothForApprovedRegistration(registration.getId());
    }

    @Transactional
    public Optional<Booth> ensureBoothForApprovedRegistration(Integer registrationId) {
        if (registrationId == null) {
            return Optional.empty();
        }

        ExhibitorRegistration registration = exhibitorRegistrationService.findRegistrationWithRelationsById(registrationId)
                .orElse(null);

        if (registration == null) {
            log.error("[PB-001/002] Registration not found for ID: {}", registrationId);
            throw new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
        }

        if (registration.getStatus() != ExhibitorRegistrationStatus.APPROVED) {
            return Optional.empty();
        }

        Optional<Booth> existingBooth = boothRepository.findByExhibitorRegistrationId(registration.getId());
        if (existingBooth.isPresent()) {
            return existingBooth;
        }

        Company company;
        try {
            company = registration.getCompany();
            if (company == null) {
                log.error("[PB-001/002] Company missing for registration ID: {}, UUID: {}", registration.getId(),
                        registration.getUuid());
                throw new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID);
            }
            if (company.getOwnerUser() == null) {
                log.error("[PB-001/002] Company owner user missing for company ID: {}, registration ID: {}, UUID: {}",
                        company.getId(), registration.getId(), registration.getUuid());
                throw new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID);
            }
            if (registration.getExhibitionPackage() == null) {
                log.error("[PB-001/002] Exhibition package missing for registration ID: {}, UUID: {}",
                        registration.getId(), registration.getUuid());
                throw new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID);
            }
        } catch (EntityNotFoundException | ObjectNotFoundException e) {
            log.error("[PB-001/002] Exception during relation resolution for registration ID: {}, UUID: {}",
                    registration.getId(), registration.getUuid(), e);
            throw new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID);
        }

        Booth booth = Booth.builder()
                .name(registration.getBoothName())
                .description(registration.getBoothDescription())
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .createdBy(company.getOwnerUser())
                .company(company)
                .exhibitorRegistration(registration)
                .build();

        return Optional.of(boothRepository.saveAndFlush(booth));
    }
}
