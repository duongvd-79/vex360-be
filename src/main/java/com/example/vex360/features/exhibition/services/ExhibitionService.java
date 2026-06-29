package com.example.vex360.features.exhibition.services;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionSummaryResponseDTO;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.ExhibitionStatus;

public interface ExhibitionService {
    ExhibitionResponseDTO createExhibition(User organizer, CreateExhibitionRequest request, MultipartFile keyVisual);

    ExhibitionResponseDTO getExhibitionByUuid(UUID uuid);

    ExhibitionPackageResponseDTO configureExhibitionPackage(User organizer, UUID uuid,
            ConfigureExhibitionPackageRequest request);

    PageResponse<ExhibitionResponseDTO> searchExhibitionsForAdmin(
            String keyword, ExhibitionStatus status, String category,
            LocalDate startDate, LocalDate endDate, Pageable pageable);

    ExhibitionSummaryResponseDTO getExhibitionSummary();

    ExhibitionResponseDTO getExhibitionDetailForAdmin(UUID uuid);

    PageResponse<ExhibitionResponseDTO> searchExhibitionsForOrganizer(
            User organizer, String keyword, ExhibitionStatus status, String category,
            LocalDate startDate, LocalDate endDate, Pageable pageable);

    ExhibitionResponseDTO getExhibitionDetailForOrganizer(User organizer, UUID uuid);

    ExhibitionResponseDTO updateExhibitionForOrganizer(User organizer, UUID uuid, CreateExhibitionRequest request, MultipartFile keyVisual);

    ExhibitionResponseDTO updateExhibitionMedia(User organizer, UUID uuid, MultipartFile trailerVideo, MultipartFile floorPlan, MultipartFile guideline);

    ExhibitionResponseDTO approveExhibition(User admin, UUID uuid);

    ExhibitionResponseDTO rejectExhibition(User admin, UUID uuid, com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest request);
}
