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
import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateSelectionResponseDTO;
import com.example.vex360.features.packagetemplate.mapper.PackageTemplateMapper;
import com.example.vex360.features.packagetemplate.repositories.PackageTemplateRepository;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.user.entities.User;
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
    public List<PackageTemplateSelectionResponseDTO> getActivePackageTemplates() {
        return packageTemplateRepository.findByStatus(
                PackageTemplateStatus.ACTIVE,
                Sort.by(Sort.Order.asc(PackageTemplate::getPrice), Sort.Order.asc(PackageTemplate::getName)))
                .stream()
                .map(packageTemplateMapper::toSelectionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PackageTemplateResponseDTO getPackageTemplateById(UUID id) {
        return packageTemplateMapper.toResponse(getPackageTemplate(id));
    }

    @Transactional(readOnly = true)
    public PackageTemplateSelectionResponseDTO getActivePackageTemplateById(UUID id) {
        return packageTemplateMapper.toSelectionResponse(getActivePackageTemplateEntity(id));
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
        template.setListingPriority(request.getListingPriority());

        return packageTemplateMapper.toResponse(packageTemplateRepository.save(template));
    }

    @Transactional
    public PackageTemplateResponseDTO updatePackageTemplateStatus(
            UUID id,
            UpdatePackageTemplateStatusRequest request) {
        PackageTemplate template = getPackageTemplate(id);
        if (template.isDefault() && request.getStatus() != PackageTemplateStatus.ACTIVE) {
            throw new AppException(ErrorCode.PACKAGE_TEMPLATE_DEFAULT_MUST_BE_ACTIVE);
        }
        template.setStatus(request.getStatus());
        return packageTemplateMapper.toResponse(packageTemplateRepository.save(template));
    }

    @Transactional
    public PackageTemplateResponseDTO setDefaultPackageTemplate(UUID id) {
        List<PackageTemplate> templates = packageTemplateRepository.findAllForUpdate();
        PackageTemplate target = templates.stream()
                .filter(template -> template.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));
        if (target.getStatus() != PackageTemplateStatus.ACTIVE) {
            throw new AppException(ErrorCode.PACKAGE_TEMPLATE_DEFAULT_MUST_BE_ACTIVE);
        }

        templates.forEach(template -> template.setDefault(false));
        packageTemplateRepository.saveAllAndFlush(templates);
        target.setDefault(true);
        return packageTemplateMapper.toResponse(packageTemplateRepository.save(target));
    }

    @Transactional(readOnly = true)
    public PackageTemplate getActivePackageTemplateEntity(UUID id) {
        return packageTemplateRepository.findByIdAndStatus(id, PackageTemplateStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PackageTemplate getDefaultActivePackageTemplateEntity() {
        return packageTemplateRepository
                .findByIsDefaultTrueAndStatus(PackageTemplateStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_DEFAULT_NOT_CONFIGURED));
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
