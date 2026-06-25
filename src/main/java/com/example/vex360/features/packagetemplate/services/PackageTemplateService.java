package com.example.vex360.features.packagetemplate.services;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.packagetemplate.dtos.request.CreatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.request.UpdatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.request.UpdatePackageTemplateStatusRequest;
import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.features.packagetemplate.mapper.PackageTemplateMapper;
import com.example.vex360.features.packagetemplate.repositories.PackageTemplateRepository;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.PackageTemplateStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PackageTemplateService {
    private final PackageTemplateRepository packageTemplateRepository;
    private final PackageTemplateMapper packageTemplateMapper;

    @Transactional
    public PackageTemplateResponseDTO createPackageTemplate(User currentUser, CreatePackageTemplateRequest request) {
        validateCurrentUser(currentUser);
        String name = request.getName().trim();
        if (packageTemplateRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.PACKAGE_TEMPLATE_NAME_DUPLICATED);
        }

        PackageTemplate template = PackageTemplate.builder()
                .createdBy(currentUser)
                .name(name)
                .description(request.getDescription().trim())
                .price(request.getPrice())
                .currency(normalizeCurrency(request.getCurrency()))
                .maxProductsPerBooth(request.getMaxProductsPerBooth())
                .maxEmbeddedVideosPerBooth(request.getMaxEmbeddedVideosPerBooth())
                .maxPanoramasPerBooth(request.getMaxPanoramasPerBooth())
                .maxHotspotsPerBooth(request.getMaxHotspotsPerBooth())
                .storageLimitMb(request.getStorageLimitMb())
                .listingPriority(request.getListingPriority())
                .status(PackageTemplateStatus.ACTIVE)
                .build();

        return packageTemplateMapper.toResponse(packageTemplateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public PageResponse<PackageTemplateResponseDTO> getPackageTemplates(
            String keyword,
            PackageTemplateStatus status,
            Pageable pageable) {
        Page<PackageTemplateResponseDTO> templates = packageTemplateRepository
                .searchPackageTemplates(normalizeKeyword(keyword), status, pageable)
                .map(packageTemplateMapper::toResponse);
        return PageResponse.from(templates);
    }

    @Transactional(readOnly = true)
    public List<PackageTemplateResponseDTO> getActivePackageTemplates() {
        return packageTemplateRepository.findByStatus(
                        PackageTemplateStatus.ACTIVE,
                        Sort.by(Sort.Order.asc("price"), Sort.Order.asc("name")))
                .stream()
                .map(packageTemplateMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PackageTemplateResponseDTO getPackageTemplateById(UUID id) {
        return packageTemplateMapper.toResponse(getPackageTemplate(id));
    }

    @Transactional(readOnly = true)
    public PackageTemplateResponseDTO getActivePackageTemplateById(UUID id) {
        PackageTemplate template = packageTemplateRepository.findByIdAndStatus(id, PackageTemplateStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));
        return packageTemplateMapper.toResponse(template);
    }

    @Transactional
    public PackageTemplateResponseDTO updatePackageTemplate(UUID id, UpdatePackageTemplateRequest request) {
        PackageTemplate template = getPackageTemplate(id);
        String name = request.getName().trim();
        if (packageTemplateRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AppException(ErrorCode.PACKAGE_TEMPLATE_NAME_DUPLICATED);
        }

        template.setName(name);
        template.setDescription(request.getDescription().trim());
        template.setPrice(request.getPrice());
        template.setCurrency(normalizeCurrency(request.getCurrency()));
        template.setMaxProductsPerBooth(request.getMaxProductsPerBooth());
        template.setMaxEmbeddedVideosPerBooth(request.getMaxEmbeddedVideosPerBooth());
        template.setMaxPanoramasPerBooth(request.getMaxPanoramasPerBooth());
        template.setMaxHotspotsPerBooth(request.getMaxHotspotsPerBooth());
        template.setStorageLimitMb(request.getStorageLimitMb());
        template.setListingPriority(request.getListingPriority());

        return packageTemplateMapper.toResponse(packageTemplateRepository.save(template));
    }

    @Transactional
    public PackageTemplateResponseDTO updatePackageTemplateStatus(
            UUID id,
            UpdatePackageTemplateStatusRequest request) {
        PackageTemplate template = getPackageTemplate(id);
        template.setStatus(request.getStatus());
        return packageTemplateMapper.toResponse(packageTemplateRepository.save(template));
    }

    private PackageTemplate getPackageTemplate(UUID id) {
        return packageTemplateRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));
    }

    private void validateCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "VND";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
