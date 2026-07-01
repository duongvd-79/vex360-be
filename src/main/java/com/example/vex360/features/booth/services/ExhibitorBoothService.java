package com.example.vex360.features.booth.services;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.UpdateBoothRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorBoothService {
    private static final Set<String> ALLOWED_THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");

    private final BoothRepository boothRepository;
    private final CompanyRepository companyRepository;
    private final CloudService cloudService;
    private final BoothMapper boothMapper;

    @Transactional(readOnly = true)
    public PageResponse<BoothResponseDTO> getBooths(User currentUser, Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Page<BoothResponseDTO> booths = boothRepository.findCompanyBooths(company.getId(), pageable)
                .map(boothMapper::toBoothResponseDTO);
        return PageResponse.from(booths);
    }

    @Transactional(readOnly = true)
    public BoothResponseDTO getBoothById(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        return boothMapper.toBoothResponseDTO(getBoothForCompany(boothId, company));
    }

    @Transactional
    public BoothResponseDTO updateBooth(
            User currentUser,
            UUID boothId,
            UpdateBoothRequest request,
            MultipartFile thumbnail) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);

        if (request != null) {
            updateMetadata(booth, request);
        }
        if (thumbnail != null && !thumbnail.isEmpty()) {
            replaceThumbnail(booth, thumbnail);
        }

        return boothMapper.toBoothResponseDTO(boothRepository.save(booth));
    }

    private void updateMetadata(Booth booth, UpdateBoothRequest request) {
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new AppException(ErrorCode.INVALID_BOOTH);
            }
            booth.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            booth.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
        }
        if (request.getDisplayTemplateKey() != null) {
            booth.setDisplayTemplateKey(request.getDisplayTemplateKey().isBlank()
                    ? "classic"
                    : request.getDisplayTemplateKey().trim());
        }
    }

    private void replaceThumbnail(Booth booth, MultipartFile thumbnail) {
        validateThumbnail(thumbnail);
        CloudinaryResponse upload = cloudService.upload(thumbnail);
        if (hasText(booth.getThumbnailPublicId())) {
            cloudService.delete(booth.getThumbnailPublicId(), "image");
        }
        booth.setThumbnailUrl(upload.getUrl());
        booth.setThumbnailPublicId(upload.getPublicId());
    }

    private Booth getBoothForCompany(UUID boothId, Company company) {
        return boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private void validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()
                || !ALLOWED_THUMBNAIL_TYPES.contains(normalizeMimeType(thumbnail))) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
    }

    private String normalizeMimeType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
