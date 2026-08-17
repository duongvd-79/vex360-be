package com.example.vex360.features.exhibition.services;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.request.AdminExhibitionStatusFilter;
import com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.ReconcileExhibitionPackagesRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageEditContextResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.ExhibitionStatus;

public interface ExhibitionService {
	ExhibitionResponseDTO createExhibition(User organizer, CreateExhibitionRequest request,
			MultipartFile keyVisual, List<MultipartFile> sponsorLogos);

	ExhibitionResponseDTO getExhibitionByUuid(UUID uuid);

	PageResponse<ExhibitionResponseDTO> searchExhibitionsForAdmin(
			String keyword, AdminExhibitionStatusFilter status, String category,
			LocalDate startDate, LocalDate endDate, Pageable pageable);

	long countPendingExhibitions();

	ExhibitionResponseDTO getExhibitionDetailForAdmin(UUID uuid);

	PageResponse<ExhibitionResponseDTO> searchExhibitionsForOrganizer(
			User organizer, String keyword, ExhibitionStatus status, String category,
			LocalDate startDate, LocalDate endDate, Pageable pageable);

	List<Exhibition> getOrganizerExhibitions(User organizer);

	List<Object[]> countExhibitionsByStatus();

	List<Object[]> aggregateDailyCreatedExhibitions(Instant start, Instant end);

	List<Exhibition> getAllExhibitions();

	long countExhibitions();

	List<Object[]> countAdminPaymentsByStatus(Instant start, Instant end);

	List<Object[]> aggregateAdminPaidMetrics(Instant start, Instant end);

	List<Object[]> aggregateAdminDailyRevenue(Instant start, Instant end);

	Map<Integer, Long> aggregateRevenueByExhibition(List<Integer> exhibitionIds, Instant start, Instant end);

	Map<Integer, Long> aggregateSystemRevenueByExhibition(List<Integer> exhibitionIds, Instant start, Instant end);

	Map<Integer, Long> aggregateProfitByExhibition(List<Integer> exhibitionIds, Instant start, Instant end);

	Exhibition findExhibitionEntityByUuid(UUID uuid);

	Map<Integer, Long> countRegistrationsByStatusGroupedByExhibition(List<Integer> exhibitionIds,
			ExhibitorRegistrationStatus status);

	List<Object[]> aggregateDailyRegistrationSubmissions(List<Integer> exhibitionIds, Instant start, Instant end);

	long countApprovedRegistrationsForExhibition(Integer exhibitionId);

	List<Object[]> aggregateOrganizerDailyRevenue(List<Integer> exhibitionIds, Instant start, Instant end);

	List<Object[]> aggregateOrganizerPackageRevenue(List<Integer> exhibitionIds, Instant start, Instant end);

	List<Object[]> aggregateDailyRevenueForExhibition(Integer exhibitionId, Instant start, Instant end);

	List<Object[]> aggregatePaidPackageRevenueForExhibition(Integer exhibitionId, Instant start, Instant end);

	ExhibitionResponseDTO getExhibitionDetailForOrganizer(User organizer, UUID uuid);

	ExhibitionResponseDTO updateExhibitionForOrganizer(User organizer, UUID uuid, CreateExhibitionRequest request,
			MultipartFile keyVisual);

	PageResponse<ExhibitionResponseDTO> searchExhibitionsForVisitor(
			String keyword, ExhibitionStatus status, String category,
			LocalDate startDate, LocalDate endDate, Pageable pageable);

	PageResponse<ExhibitionResponseDTO> searchExhibitionsForExhibitor(
			String keyword, String category, LocalDate startDate, LocalDate endDate, Pageable pageable);

	ExhibitionResponseDTO getExhibitionDetailForExhibitor(UUID uuid);

	ExhibitionResponseDTO publishExhibition(User organizer, UUID uuid);

	ExhibitionResponseDTO updateExhibitionMedia(User organizer, UUID uuid, MultipartFile trailerVideo,
			MultipartFile floorPlan, MultipartFile guideline);

	ExhibitionResponseDTO approveExhibition(User admin, UUID uuid);

	ExhibitionResponseDTO rejectExhibition(User admin, UUID uuid,
			RejectExhibitionRequest request);

	// Sponsor Media CRU
	ExhibitionResponseDTO uploadSponsorLogo(User organizer, UUID uuid, String name, MultipartFile file);

	ExhibitionResponseDTO updateSponsorLogo(User organizer, UUID uuid, UUID assetId, String name, MultipartFile file);

	ExhibitionResponseDTO deleteSponsorLogo(User organizer, UUID uuid, UUID assetId);

	// Exhibition Package CRU
	ExhibitionPackageResponseDTO addExhibitionPackage(User organizer, UUID uuid,
			ConfigureExhibitionPackageRequest request);

	ExhibitionPackageResponseDTO updateExhibitionPackage(User organizer, UUID uuid, Integer packageId,
			ConfigureExhibitionPackageRequest request);

	ExhibitionResponseDTO deleteExhibitionPackage(User organizer, UUID uuid, Integer packageId);

	ExhibitionPackageEditContextResponseDTO getExhibitionPackageEditContext(User organizer, UUID uuid);

	ExhibitionPackageEditContextResponseDTO reconcileExhibitionPackages(User organizer, UUID uuid,
			ReconcileExhibitionPackagesRequest request);

	Exhibition findExhibitionForUpdate(Integer id);

	Exhibition findExhibitionForUpdate(UUID uuid);

	boolean isAssetReferenced(String publicId);
}
