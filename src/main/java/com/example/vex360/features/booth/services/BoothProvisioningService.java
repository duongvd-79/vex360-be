package com.example.vex360.features.booth.services;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothProvisioningService {
    private final BoothRepository boothRepository;

    @Transactional
    public Optional<Booth> ensureBoothForApprovedRegistration(ExhibitorRegistration registration) {
        if (registration == null || registration.getId() == null) {
            return Optional.empty();
        }
        if (registration.getStatus() != ExhibitorRegistrationStatus.APPROVED) {
            return Optional.empty();
        }

        Optional<Booth> existingBooth = boothRepository.findByExhibitorRegistrationId(registration.getId());
        if (existingBooth.isPresent()) {
            return existingBooth;
        }

        Company company = registration.getCompany();

        Booth booth = Booth.builder()
                .name(company != null ? company.getName() : null)
                .description(null)
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .createdBy(company != null ? company.getOwnerUser() : null)
                .company(company)
                .exhibitorRegistration(registration)
                .build();

        return Optional.of(boothRepository.save(booth));
    }
}
