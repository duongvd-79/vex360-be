package com.example.vex360.features.exhibition.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionReviewHistoryResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionReviewRequest;
import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;
import com.example.vex360.features.exhibition.mapper.ExhibitionReviewHistoryMapper;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionReviewRequestRepository;
import com.example.vex360.features.exhibition.services.impl.ExhibitionReviewHistoryServiceImpl;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ExhibitionReviewHistoryServiceTest {

    @Mock
    private ExhibitionReviewRequestRepository reviewRequestRepository;

    @Mock
    private ExhibitionRepository exhibitionRepository;

    @Mock
    private ExhibitionAssetRepository exhibitionAssetRepository;

    private ExhibitionReviewHistoryMapper mapper = Mappers.getMapper(ExhibitionReviewHistoryMapper.class);

    private ExhibitionReviewHistoryServiceImpl reviewHistoryService;

    private User organizer;
    private User admin;
    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        reviewHistoryService = new ExhibitionReviewHistoryServiceImpl(
                reviewRequestRepository,
                exhibitionRepository,
                exhibitionAssetRepository,
                mapper);

        organizer = User.builder()
                .id(UUID.randomUUID())
                .fullName("Organizer Test")
                .role(Role.ORGANIZER)
                .build();

        admin = User.builder()
                .id(UUID.randomUUID())
                .fullName("Admin Test")
                .role(Role.ADMIN)
                .build();

        exhibition = Exhibition.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .name("Exhibition Test")
                .category("Tech")
                .description("Desc")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .estimatedBooths(30)
                .status(ExhibitionStatus.PENDING)
                .organizer(organizer)
                .build();
    }

    @Test
    void recordInitialSubmission_ShouldCreateVersion1PendingRound() {
        reviewHistoryService.recordInitialSubmission(exhibition, organizer, "http://keyvisual.url");

        ArgumentCaptor<ExhibitionReviewRequest> captor = ArgumentCaptor.forClass(ExhibitionReviewRequest.class);
        verify(reviewRequestRepository).save(captor.capture());

        ExhibitionReviewRequest saved = captor.getValue();
        assertEquals(1, saved.getVersionNumber());
        assertEquals(ExhibitionReviewStatus.PENDING, saved.getStatus());
        assertEquals(organizer, saved.getSubmittedBy());
        assertNotNull(saved.getSubmittedAt());
        assertFalse(saved.getLegacyIncomplete());
        assertNotNull(saved.getContentSnapshotJson());
        assertTrue(saved.getContentSnapshotJson().contains("http://keyvisual.url"));
    }

    @Test
    void recordResubmissionOrUpdate_WhenPending_ShouldUpdateSnapshotWithoutIncreasingVersion() {
        ExhibitionReviewRequest existingRound = ExhibitionReviewRequest.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .versionNumber(1)
                .status(ExhibitionReviewStatus.PENDING)
                .submittedBy(organizer)
                .submittedAt(Instant.now())
                .contentSnapshotJson("old json")
                .build();

        when(reviewRequestRepository.findFirstByExhibitionIdOrderByVersionNumberDesc(1))
                .thenReturn(Optional.of(existingRound));

        reviewHistoryService.recordResubmissionOrUpdate(exhibition, organizer, "http://updated.url");

        verify(reviewRequestRepository).save(existingRound);
        assertTrue(existingRound.getContentSnapshotJson().contains("http://updated.url"));
        assertEquals(1, existingRound.getVersionNumber());
    }

    @Test
    void recordResubmissionOrUpdate_WhenRejected_ShouldCreateVersion2PendingRound() {
        ExhibitionReviewRequest round1 = ExhibitionReviewRequest.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .versionNumber(1)
                .status(ExhibitionReviewStatus.REJECTED)
                .submittedBy(organizer)
                .submittedAt(Instant.now().minusSeconds(3600))
                .reviewedBy(admin)
                .reviewedAt(Instant.now())
                .rejectedReason("Invalid docs")
                .build();

        when(reviewRequestRepository.findFirstByExhibitionIdOrderByVersionNumberDesc(1))
                .thenReturn(Optional.of(round1));

        reviewHistoryService.recordResubmissionOrUpdate(exhibition, organizer, "http://new.url");

        ArgumentCaptor<ExhibitionReviewRequest> captor = ArgumentCaptor.forClass(ExhibitionReviewRequest.class);
        verify(reviewRequestRepository).save(captor.capture());

        ExhibitionReviewRequest round2 = captor.getValue();
        assertEquals(2, round2.getVersionNumber());
        assertEquals(ExhibitionReviewStatus.PENDING, round2.getStatus());
        assertEquals(organizer, round2.getSubmittedBy());
    }

    @Test
    void recordReviewResult_Approve_ShouldSetStatusApprovedAndReviewedBy() {
        ExhibitionReviewRequest pendingRound = ExhibitionReviewRequest.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .versionNumber(1)
                .status(ExhibitionReviewStatus.PENDING)
                .build();

        when(reviewRequestRepository.findFirstByExhibitionIdAndStatusForUpdate(1, ExhibitionReviewStatus.PENDING))
                .thenReturn(Optional.of(pendingRound));

        reviewHistoryService.recordReviewResult(exhibition, admin, ExhibitionReviewStatus.APPROVED, null);

        verify(reviewRequestRepository).save(pendingRound);
        assertEquals(ExhibitionReviewStatus.APPROVED, pendingRound.getStatus());
        assertEquals(admin, pendingRound.getReviewedBy());
        assertNotNull(pendingRound.getReviewedAt());
        assertNull(pendingRound.getRejectedReason());
    }

    @Test
    void recordReviewResult_Reject_ShouldSetStatusRejectedAndReason() {
        ExhibitionReviewRequest pendingRound = ExhibitionReviewRequest.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .versionNumber(1)
                .status(ExhibitionReviewStatus.PENDING)
                .build();

        when(reviewRequestRepository.findFirstByExhibitionIdAndStatusForUpdate(1, ExhibitionReviewStatus.PENDING))
                .thenReturn(Optional.of(pendingRound));

        reviewHistoryService.recordReviewResult(exhibition, admin, ExhibitionReviewStatus.REJECTED, "Incomplete info");

        verify(reviewRequestRepository).save(pendingRound);
        assertEquals(ExhibitionReviewStatus.REJECTED, pendingRound.getStatus());
        assertEquals(admin, pendingRound.getReviewedBy());
        assertEquals("Incomplete info", pendingRound.getRejectedReason());
    }

    @Test
    void getReviewHistoryForAdmin_ShouldReturnAllRoundsNewestFirst() {
        UUID exhUuid = exhibition.getUuid();
        ExhibitionReviewRequest request = ExhibitionReviewRequest.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .versionNumber(1)
                .status(ExhibitionReviewStatus.PENDING)
                .reviewedBy(admin)
                .build();

        when(exhibitionRepository.findByUuid(exhUuid)).thenReturn(Optional.of(exhibition));
        when(reviewRequestRepository.findByExhibitionUuidOrderByVersionNumberDesc(exhUuid))
                .thenReturn(List.of(request));

        List<ExhibitionReviewHistoryResponseDTO> result = reviewHistoryService.getReviewHistoryForAdmin(exhUuid);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getVersionNumber());
        assertEquals("Admin Test", result.get(0).getReviewedByName());
    }

    @Test
    void getReviewHistoryForOrganizer_Owner_ShouldReturnHistory() {
        UUID exhUuid = exhibition.getUuid();
        ExhibitionReviewRequest request = ExhibitionReviewRequest.builder()
                .id(UUID.randomUUID())
                .exhibition(exhibition)
                .versionNumber(1)
                .status(ExhibitionReviewStatus.PENDING)
                .build();

        when(exhibitionRepository.findByUuid(exhUuid)).thenReturn(Optional.of(exhibition));
        when(reviewRequestRepository.findByExhibitionUuidOrderByVersionNumberDesc(exhUuid))
                .thenReturn(List.of(request));

        List<ExhibitionReviewHistoryResponseDTO> result = reviewHistoryService.getReviewHistoryForOrganizer(organizer,
                exhUuid);

        assertEquals(1, result.size());
    }

    @Test
    void getReviewHistoryForOrganizer_NotOwner_ShouldThrowExhibitionNotFound() {
        UUID exhUuid = exhibition.getUuid();
        User anotherOrganizer = User.builder().id(UUID.randomUUID()).role(Role.ORGANIZER).build();

        when(exhibitionRepository.findByUuid(exhUuid)).thenReturn(Optional.of(exhibition));

        AppException exception = assertThrows(AppException.class,
                () -> reviewHistoryService.getReviewHistoryForOrganizer(anotherOrganizer, exhUuid));

        assertEquals(ErrorCode.EXHIBITION_NOT_FOUND, exception.getErrorCode());
    }
}
