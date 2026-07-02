package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

import com.example.vex360.features.booth.dtos.request.UpdateBoothRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ExhibitorBoothServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CloudService cloudService;

    private ExhibitorBoothService exhibitorBoothService;
    private User exhibitorUser;
    private Company company;

    @BeforeEach
    void setup() {
        exhibitorBoothService = new ExhibitorBoothService(
                boothRepository,
                companyRepository,
                cloudService,
                Mappers.getMapper(BoothMapper.class));
        exhibitorUser = User.builder()
                .id(UUID.randomUUID())
                .email("exhibitor@example.com")
                .build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(exhibitorUser)
                .name("VEX Company")
                .build();
    }

    @Test
    void updateBoothReplacesThumbnailAndDeletesOldCloudinaryFile() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder()
                .id(boothId)
                .name("Old Booth")
                .description("Old description")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .company(company)
                .thumbnailUrl("https://old.example/thumbnail.png")
                .thumbnailPublicId("old_public_id")
                .build();
        MockMultipartFile thumbnail = new MockMultipartFile(
                "thumbnail",
                "thumbnail.png",
                "image/png",
                "image".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://new.example/thumbnail.png")
                .publicId("new_public_id")
                .fileType("image/png")
                .build();

        when(companyRepository.findByOwnerUserId(exhibitorUser.getId())).thenReturn(Optional.of(company));
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(cloudService.upload(thumbnail)).thenReturn(upload);
        when(boothRepository.save(any(Booth.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BoothResponseDTO response = exhibitorBoothService.updateBooth(
                exhibitorUser,
                boothId,
                new UpdateBoothRequest("New Booth", "", null),
                thumbnail);

        assertEquals("New Booth", response.getName());
        assertEquals("https://new.example/thumbnail.png", response.getThumbnailUrl());
        verify(cloudService).delete("old_public_id", "image");
    }
}
