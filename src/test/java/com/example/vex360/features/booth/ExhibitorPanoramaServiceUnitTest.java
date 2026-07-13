package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothBenefitGuardService;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ExhibitorPanoramaServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private PanoramaRepository panoramaRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private CloudService cloudService;

    @Mock
    private BoothBenefitGuardService boothBenefitGuardService;

    @Mock
    private BoothReviewPolicyService boothReviewPolicyService;

    @Mock
    private BoothReviewRequestRepository boothReviewRequestRepository;

    private ExhibitorPanoramaService exhibitorPanoramaService;
    private User exhibitorUser;
    private Company company;
    private Booth booth;

    @BeforeEach
    void setup() {
        exhibitorPanoramaService = new ExhibitorPanoramaService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                companyService,
                cloudService,
                Mappers.getMapper(BoothMapper.class),
                boothBenefitGuardService,
                boothReviewPolicyService,
                boothReviewRequestRepository);
        exhibitorUser = User.builder().id(UUID.randomUUID()).email("exhibitor@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitorUser).name("VEX Company").build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Runtime Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .company(company)
                .createdBy(exhibitorUser)
                .build();
    }

    @Test
    void createPanorama_WhenQuotaExceeded_DoesNotUploadImage() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "panorama.jpg",
                "image/jpeg",
                "image".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED))
                .when(boothBenefitGuardService)
                .assertCanAddPanorama(booth);

        AppException exception = assertThrows(AppException.class, () -> exhibitorPanoramaService.createPanorama(
                exhibitorUser,
                booth.getId(),
                new CreateExhibitorPanoramaRequest("Entrance", null, true),
                image));

        assertSame(ErrorCode.BOOTH_QUOTA_EXCEEDED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }
}
