package com.example.vex360.features.exhibition.services.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.mapper.ExhibitionMapper;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.packagetemplate.repositories.PackageTemplateRepository;
import com.example.vex360.shared.entities.Exhibition;
import com.example.vex360.shared.entities.ExhibitionPackage;
import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionServiceImpl implements ExhibitionService {

    private final ExhibitionRepository exhibitionRepository;
    private final ExhibitionPackageRepository exhibitionPackageRepository;
    private final PackageTemplateRepository packageTemplateRepository;
    private final ExhibitionMapper exhibitionMapper;

    @Override
    @Transactional
    public ExhibitionResponseDTO createExhibition(User organizer, CreateExhibitionRequest request) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = Exhibition.builder()
                .organizer(organizer)
                .uuid(UUID.randomUUID())
                .name(request.getName().trim())
                .category(request.getCategory().trim())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .estimatedBooths(request.getEstimatedBooths())
                .status("ACTIVE")
                .build();

        exhibition = exhibitionRepository.save(exhibition);
        log.info("Created Exhibition: {} (ID: {}, UUID: {})", exhibition.getName(), exhibition.getId(), exhibition.getUuid());

        List<ExhibitionPackage> savedPackages = new ArrayList<>();
        if (request.getPackages() != null && !request.getPackages().isEmpty()) {
            if (request.getPackages().size() > 3) {
                log.error("Exhibition packages size exceeds limit of 3");
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }

            Set<com.example.vex360.shared.enums.BoothListingPriority> priorities = new HashSet<>();
            for (ConfigureExhibitionPackageRequest pkgReq : request.getPackages()) {
                PackageTemplate template = packageTemplateRepository.findById(pkgReq.getTemplateId())
                        .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

                if (pkgReq.getFinalPrice().compareTo(template.getPrice()) < 0) {
                    log.error("Package final price {} is below floor price {}", pkgReq.getFinalPrice(), template.getPrice());
                    throw new AppException(ErrorCode.VALIDATION_FAILED);
                }

                if (!priorities.add(template.getListingPriority())) {
                    log.error("Duplicate package priority type {} is not allowed", template.getListingPriority());
                    throw new AppException(ErrorCode.VALIDATION_FAILED);
                }

                ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                        .exhibition(exhibition)
                        .template(template)
                        .finalPrice(pkgReq.getFinalPrice())
                        .status("ACTIVE")
                        .build();

                savedPackages.add(exhibitionPackageRepository.save(exhibitionPackage));
            }
        }

        return exhibitionMapper.toResponse(exhibition, savedPackages);
    }


    @Override
    @Transactional(readOnly = true)
    public ExhibitionResponseDTO getExhibitionByUuid(UUID uuid) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toPublicResponse(exhibition, packages);
    }
}
