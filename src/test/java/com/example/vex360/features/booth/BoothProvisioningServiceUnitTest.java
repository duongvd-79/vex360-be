package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.services.ExhibitorRegistrationService;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.ExhibitionParticipationPolicy;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BoothProvisioningServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private ExhibitorRegistrationService exhibitorRegistrationService;

    @Mock
    private ExhibitionTimelinePolicy timelinePolicy;

    private BoothProvisioningService boothProvisioningService;
    private User exhibitorUser;
    private Company company;
    private ExhibitionPackage exhibitionPackage;

    @BeforeEach
    void setup() {
        boothProvisioningService = new BoothProvisioningService(
                boothRepository, exhibitorRegistrationService, timelinePolicy,
                new ExhibitionParticipationPolicy());
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
        exhibitionPackage = ExhibitionPackage.builder()
                .id(10)
                .exhibition(Exhibition.builder().id(20)
                        .experienceMode(com.example.vex360.shared.enums.ExhibitionExperienceMode.WITH_BOOTHS)
                        .build())
                .build();
        org.mockito.Mockito.lenient()
                .when(timelinePolicy.isRegistrationProcessingOpen(exhibitionPackage.getExhibition()))
                .thenReturn(true);
    }

    @Test
    void createsBoothForApprovedRegistration() {
        ExhibitorRegistration registration = registration(ExhibitorRegistrationStatus.APPROVED);
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(registration.getId()))
                .thenReturn(Optional.of(registration));
        when(boothRepository.findByExhibitorRegistrationId(registration.getId())).thenReturn(Optional.empty());
        when(boothRepository.saveAndFlush(any(Booth.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Booth> result = boothProvisioningService.ensureBoothForApprovedRegistration(registration);

        assertTrue(result.isPresent());
        Booth booth = result.get();
        assertEquals("Requested Booth", booth.getName());
        assertEquals("Requested Booth Description", booth.getDescription());
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
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(registration.getId()))
                .thenReturn(Optional.of(registration));
        when(boothRepository.findByExhibitorRegistrationId(registration.getId()))
                .thenReturn(Optional.of(existingBooth));

        Optional<Booth> result = boothProvisioningService.ensureBoothForApprovedRegistration(registration);

        assertTrue(result.isPresent());
        assertSame(existingBooth, result.get());
        verify(boothRepository, never()).save(any());
    }

    @Test
    void doesNotCreateBoothForPendingRegistration() {
        ExhibitorRegistration registration = registration(ExhibitorRegistrationStatus.PENDING);
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(registration.getId()))
                .thenReturn(Optional.of(registration));

        Optional<Booth> result = boothProvisioningService
                .ensureBoothForApprovedRegistration(registration);

        assertTrue(result.isEmpty());
        verify(boothRepository, never()).save(any());
    }

    @Test
    void doesNotCreateBoothAfterPreparationCloses() {
        ExhibitorRegistration registration = registration(ExhibitorRegistrationStatus.APPROVED);
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(registration.getId()))
                .thenReturn(Optional.of(registration));
        when(timelinePolicy.isRegistrationProcessingOpen(exhibitionPackage.getExhibition())).thenReturn(false);

        Optional<Booth> result = boothProvisioningService.ensureBoothForApprovedRegistration(registration);

        assertTrue(result.isEmpty());
        verify(boothRepository, never()).saveAndFlush(any());
    }

    @Test
    void standaloneRegistrationNeverProvisionsBooth() {
        exhibitionPackage.getExhibition().setExperienceMode(
                com.example.vex360.shared.enums.ExhibitionExperienceMode.STANDALONE);
        ExhibitorRegistration registration = registration(ExhibitorRegistrationStatus.APPROVED);
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(registration.getId()))
                .thenReturn(Optional.of(registration));
        when(boothRepository.findByExhibitorRegistrationId(registration.getId())).thenReturn(Optional.empty());

        Optional<Booth> result = boothProvisioningService.ensureBoothForApprovedRegistration(registration);

        assertTrue(result.isEmpty());
        verify(boothRepository, never()).saveAndFlush(any());
    }

    @Test
    void ensureBoothForApprovedRegistration_NullRegistration_ReturnsEmpty() {
        Optional<Booth> result = boothProvisioningService
                .ensureBoothForApprovedRegistration((ExhibitorRegistration) null);
        assertTrue(result.isEmpty());
    }

    @Test
    void ensureBoothForApprovedRegistration_NullRegistrationId_ReturnsEmpty() {
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(null)
                .company(company)
                .exhibitionPackage(exhibitionPackage)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .build();
        Optional<Booth> result = boothProvisioningService.ensureBoothForApprovedRegistration(registration);
        assertTrue(result.isEmpty());
    }

    @Test
    void throwsExceptionWhenRegistrationCompanyMissing() {
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .company(null)
                .exhibitionPackage(exhibitionPackage)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .build();
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(1)).thenReturn(Optional.of(registration));

        AppException ex = assertThrows(AppException.class,
                () -> boothProvisioningService.ensureBoothForApprovedRegistration(1));
        assertEquals(ErrorCode.REGISTRATION_COMPANY_MISSING, ex.getErrorCode());
    }

    @Test
    void throwsExceptionWhenCompanyOwnerUserMissing() {
        Company orphanCompany = Company.builder()
                .id(UUID.randomUUID())
                .name("No Owner Co")
                .ownerUser(null)
                .build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .company(orphanCompany)
                .exhibitionPackage(exhibitionPackage)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .build();
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(1)).thenReturn(Optional.of(registration));

        AppException ex = assertThrows(AppException.class,
                () -> boothProvisioningService.ensureBoothForApprovedRegistration(1));
        assertEquals(ErrorCode.REGISTRATION_COMPANY_OWNER_MISSING, ex.getErrorCode());
    }

    @Test
    void throwsExceptionWhenExhibitionPackageMissing() {
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .company(company)
                .exhibitionPackage(null)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .build();
        when(exhibitorRegistrationService.findRegistrationWithRelationsById(1)).thenReturn(Optional.of(registration));

        AppException ex = assertThrows(AppException.class,
                () -> boothProvisioningService.ensureBoothForApprovedRegistration(1));
        assertEquals(ErrorCode.REGISTRATION_PACKAGE_MISSING, ex.getErrorCode());
    }

    private ExhibitorRegistration registration(ExhibitorRegistrationStatus status) {
        return ExhibitorRegistration.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .company(company)
                .exhibitionPackage(exhibitionPackage)
                .status(status)
                .boothName("Requested Booth")
                .boothDescription("Requested Booth Description")
                .build();
    }
}
