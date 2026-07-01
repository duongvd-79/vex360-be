package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.ExhibitorRegistration;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;

@ExtendWith(MockitoExtension.class)
class BoothProvisioningServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private CompanyRepository companyRepository;

    private BoothProvisioningService boothProvisioningService;
    private User exhibitorUser;
    private Company company;

    @BeforeEach
    void setup() {
        boothProvisioningService = new BoothProvisioningService(boothRepository, companyRepository);
        exhibitorUser = User.builder()
                .id(UUID.randomUUID())
                .email("exhibitor@example.com")
                .build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(exhibitorUser)
                .name("VEX Company")
                .description("Company description")
                .build();
    }

    @Test
    void createsBoothForApprovedRegistration() {
        ExhibitorRegistration registration = registration(ExhibitorRegistrationStatus.APPROVED);
        when(boothRepository.findByExhibitorRegistrationId(registration.getId())).thenReturn(Optional.empty());
        when(companyRepository.findByOwnerUserId(exhibitorUser.getId())).thenReturn(Optional.of(company));
        when(boothRepository.save(any(Booth.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Booth> result = boothProvisioningService.ensureBoothForApprovedRegistration(registration);

        assertTrue(result.isPresent());
        Booth booth = result.get();
        assertEquals("VEX Company", booth.getName());
        assertNull(booth.getDescription());
        assertEquals(BoothStatus.DRAFT, booth.getStatus());
        assertFalse(booth.getIsTemplate());
        assertSame(company, booth.getCompany());
        assertSame(registration, booth.getExhibitorRegistration());
    }

    @Test
    void returnsExistingBoothWithoutCreatingDuplicate() {
        ExhibitorRegistration registration = registration(ExhibitorRegistrationStatus.APPROVED);
        Booth existingBooth = Booth.builder()
                .id(UUID.randomUUID())
                .isTemplate(false)
                .company(company)
                .exhibitorRegistration(registration)
                .build();
        when(boothRepository.findByExhibitorRegistrationId(registration.getId())).thenReturn(Optional.of(existingBooth));

        Optional<Booth> result = boothProvisioningService.ensureBoothForApprovedRegistration(registration);

        assertTrue(result.isPresent());
        assertSame(existingBooth, result.get());
        verify(boothRepository, never()).save(any());
    }

    @Test
    void doesNotCreateBoothForPendingRegistration() {
        Optional<Booth> result = boothProvisioningService
                .ensureBoothForApprovedRegistration(registration(ExhibitorRegistrationStatus.PENDING));

        assertTrue(result.isEmpty());
        verify(boothRepository, never()).save(any());
    }

    private ExhibitorRegistration registration(ExhibitorRegistrationStatus status) {
        return ExhibitorRegistration.builder()
                .id(1)
                .company(exhibitorUser)
                .status(status)
                .build();
    }
}
