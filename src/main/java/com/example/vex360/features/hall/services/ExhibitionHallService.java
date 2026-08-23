package com.example.vex360.features.hall.services;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.hall.dtos.request.CreateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.request.UpdateExhibitionHallRequest;
import com.example.vex360.features.hall.dtos.response.ExhibitionHallResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.enums.HallStatus;
import com.example.vex360.features.hall.mapper.ExhibitionHallMapper;
import com.example.vex360.features.hall.repositories.ExhibitionHallRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitionHallService {
    private static final Set<String> ALLOWED_BACKGROUND_MUSIC_TYPES = Set.of("audio/mpeg", "audio/mp3");
    private static final String BACKGROUND_MUSIC_FOLDER = "hall-background-music";
    private static final long BACKGROUND_MUSIC_MAX_SIZE_MB = 10;
    private static final String CLOUDINARY_AUDIO_RESOURCE_TYPE = "video";

    private final ExhibitionHallRepository hallRepository;
    private final ExhibitionService exhibitionService;
    private final ExhibitionTimelinePolicy timelinePolicy;
    private final CloudService cloudService;
    private final PanoramaImageCleanupService assetCleanupService;
    private final ExhibitionHallMapper hallMapper;

    @Transactional
    public ExhibitionHallResponseDTO createHall(
            User organizer,
            UUID exhibitionUuid,
            CreateExhibitionHallRequest request) {
        requireOrganizer(organizer);
        Exhibition exhibition = exhibitionService.findExhibitionForUpdate(exhibitionUuid);
        requireOwner(organizer, exhibition);
        requireEditableExhibition(exhibition);

        if (hallRepository.existsByExhibitionId(exhibition.getId())) {
            throw new AppException(ErrorCode.HALL_ALREADY_EXISTS);
        }

        ExhibitionHall hall = ExhibitionHall.builder()
                .exhibition(exhibition)
                .name(request.getName().trim())
                .description(request.getDescription())
                .status(HallStatus.DRAFT)
                .build();
        try {
            return hallMapper.toResponse(hallRepository.saveAndFlush(hall));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.HALL_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public ExhibitionHallResponseDTO getHall(User organizer, UUID exhibitionUuid) {
        return hallMapper.toResponse(findOwnedHallEntity(organizer, exhibitionUuid));
    }

    @Transactional
    public ExhibitionHallResponseDTO updateHall(
            User organizer,
            UUID exhibitionUuid,
            UpdateExhibitionHallRequest request) {
        ExhibitionHall hall = findEditableHallForUpdate(organizer, exhibitionUuid);

        hall.setName(request.getName().trim());
        hall.setDescription(request.getDescription());
        return hallMapper.toResponse(hallRepository.save(hall));
    }

    @Transactional
    public ExhibitionHallResponseDTO updateBackgroundMusic(
            User organizer,
            UUID exhibitionUuid,
            MultipartFile backgroundMusic) {
        ExhibitionHall hall = findEditableHallForUpdate(organizer, exhibitionUuid);
        validateBackgroundMusic(backgroundMusic);

        String oldPublicId = hall.getBackgroundMusicPublicId();
        CloudinaryResponse upload = cloudService.uploadToFolder(backgroundMusic, BACKGROUND_MUSIC_FOLDER);
        hall.setBackgroundMusicUrl(upload.getUrl());
        hall.setBackgroundMusicPublicId(upload.getPublicId());
        hall.setBackgroundMusicFileName(backgroundMusic.getOriginalFilename());
        hall.setBackgroundMusicFileSize(upload.getFileSize() == null
                ? backgroundMusic.getSize()
                : upload.getFileSize());

        try {
            ExhibitionHall savedHall = hallRepository.saveAndFlush(hall);
            assetCleanupService.scheduleCleanup(oldPublicId, CLOUDINARY_AUDIO_RESOURCE_TYPE);
            return hallMapper.toResponse(savedHall);
        } catch (RuntimeException exception) {
            cleanupNewUpload(upload.getPublicId(), exception);
            throw exception;
        }
    }

    @Transactional
    public ExhibitionHallResponseDTO deleteBackgroundMusic(User organizer, UUID exhibitionUuid) {
        ExhibitionHall hall = findEditableHallForUpdate(organizer, exhibitionUuid);
        String publicId = hall.getBackgroundMusicPublicId();
        if (publicId == null || publicId.isBlank()) {
            return hallMapper.toResponse(hall);
        }

        hall.setBackgroundMusicUrl(null);
        hall.setBackgroundMusicPublicId(null);
        hall.setBackgroundMusicFileName(null);
        hall.setBackgroundMusicFileSize(null);
        ExhibitionHall savedHall = hallRepository.saveAndFlush(hall);
        assetCleanupService.scheduleCleanup(publicId, CLOUDINARY_AUDIO_RESOURCE_TYPE);
        return hallMapper.toResponse(savedHall);
    }

    @Transactional(readOnly = true)
    public ExhibitionHall findOwnedHallEntity(User organizer, UUID exhibitionUuid) {
        requireOrganizer(organizer);
        Exhibition exhibition = exhibitionService.findExhibitionEntityByUuid(exhibitionUuid);
        requireOwner(organizer, exhibition);
        return hallRepository.findByExhibitionId(exhibition.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_NOT_FOUND));
    }

    @Transactional
    public ExhibitionHall findEditableHallForUpdate(User organizer, UUID exhibitionUuid) {
        requireOrganizer(organizer);
        Exhibition exhibition = exhibitionService.findExhibitionForUpdate(exhibitionUuid);
        requireOwner(organizer, exhibition);
        requireEditableExhibition(exhibition);

        ExhibitionHall hall = hallRepository.findByExhibitionIdForUpdate(exhibition.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_NOT_FOUND));
        if (hall.getStatus() != HallStatus.DRAFT && hall.getStatus() != HallStatus.REJECTED) {
            throw new AppException(ErrorCode.HALL_CHANGES_NOT_ALLOWED);
        }
        if (hall.getStatus() == HallStatus.REJECTED) {
            hall.setStatus(HallStatus.DRAFT);
        }
        return hall;
    }

    @Transactional(readOnly = true)
    public boolean isBackgroundMusicReferenced(String publicId) {
        return publicId != null && !publicId.isBlank()
                && hallRepository.existsByBackgroundMusicPublicId(publicId);
    }

    private void validateBackgroundMusic(MultipartFile backgroundMusic) {
        String extension = FileUploadUtils.getFileExtension(backgroundMusic == null
                ? null
                : backgroundMusic.getOriginalFilename());
        String contentType = backgroundMusic == null || backgroundMusic.getContentType() == null
                ? ""
                : backgroundMusic.getContentType().toLowerCase(Locale.ROOT);
        if (backgroundMusic == null || backgroundMusic.isEmpty()
                || extension == null || !"mp3".equalsIgnoreCase(extension)
                || !ALLOWED_BACKGROUND_MUSIC_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        FileUploadUtils.validateFileSize(backgroundMusic, BACKGROUND_MUSIC_MAX_SIZE_MB);
    }

    private void cleanupNewUpload(String publicId, RuntimeException originalException) {
        try {
            cloudService.delete(publicId, CLOUDINARY_AUDIO_RESOURCE_TYPE);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private void requireOrganizer(User organizer) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private void requireOwner(User organizer, Exhibition exhibition) {
        if (exhibition.getOrganizer() == null
                || !organizer.getId().equals(exhibition.getOrganizer().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireEditableExhibition(Exhibition exhibition) {
        if (exhibition.getStatus() != ExhibitionStatus.REGISTRATION
                && exhibition.getStatus() != ExhibitionStatus.PUBLISHED) {
            throw new AppException(ErrorCode.HALL_EXHIBITION_NOT_ELIGIBLE);
        }
        if (!timelinePolicy.today().isBefore(exhibition.getStartDate())) {
            throw new AppException(ErrorCode.HALL_EXHIBITION_ALREADY_STARTED);
        }
    }
}
