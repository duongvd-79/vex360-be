package com.example.vex360.features.lead;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.lead.dtos.request.CreateBoothLeadRequest;
import com.example.vex360.features.lead.dtos.request.UpdateBoothLeadRequest;
import com.example.vex360.features.lead.entities.BoothLead;
import com.example.vex360.features.lead.enums.LeadStatus;
import com.example.vex360.features.lead.repositories.BoothLeadRepository;
import com.example.vex360.features.lead.services.BoothLeadService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoothLeadServiceUnitTest {

    @Mock
    BoothLeadRepository boothLeadRepository;
    @Mock
    BoothRepository boothRepository;
    @Mock
    ExhibitionRepository exhibitionRepository;
    @Mock
    CompanyRepository companyRepository;

    @InjectMocks
    BoothLeadService boothLeadService;

    User visitor;
    User exhibitor;
    Exhibition exhibition;
    Booth booth;

    @BeforeEach
    void setUp() {
        visitor = User.builder().id(UUID.randomUUID()).email("visitor@vex360.local").build();
        exhibitor = User.builder().id(UUID.randomUUID()).email("exhibitor@vex360.local").build();
        exhibition = Exhibition.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .name("Triển lãm thử nghiệm")
                .status(ExhibitionStatus.ACTIVE)
                .build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Gian hàng thử nghiệm")
                .status(BoothStatus.PUBLISHED)
                .isTemplate(false)
                .build();
    }

    @Test
    void submitLead_newVisitorLead_createsNormalizedLead() {
        when(exhibitionRepository.findByUuid(exhibition.getUuid())).thenReturn(Optional.of(exhibition));
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibition.getUuid(), booth.getId(), BoothStatus.PUBLISHED)).thenReturn(Optional.of(booth));
        when(boothLeadRepository.findByBoothIdAndVisitorId(booth.getId(), visitor.getId()))
                .thenReturn(Optional.empty());
        when(boothLeadRepository.save(any(BoothLead.class))).thenAnswer(invocation -> {
            BoothLead lead = invocation.getArgument(0);
            lead.setId(UUID.randomUUID());
            return lead;
        });

        var response = boothLeadService.submitLead(
                visitor,
                exhibition.getUuid(),
                booth.getId(),
                new CreateBoothLeadRequest(
                        "  Nguyễn Minh Anh  ",
                        "  MINHANH@EXAMPLE.COM ",
                        " 0901234567 ",
                        " Công ty ABC ",
                        " Quan tâm báo giá ",
                        true));

        ArgumentCaptor<BoothLead> captor = ArgumentCaptor.forClass(BoothLead.class);
        org.mockito.Mockito.verify(boothLeadRepository).save(captor.capture());
        BoothLead saved = captor.getValue();

        assertFalse(response.alreadySubmitted());
        assertEquals("Nguyễn Minh Anh", saved.getFullName());
        assertEquals("minhanh@example.com", saved.getEmail());
        assertEquals("0901234567", saved.getPhoneNumber());
        assertEquals("Công ty ABC", saved.getCompanyName());
        assertEquals(LeadStatus.NEW, saved.getStatus());
        assertEquals(visitor, saved.getVisitor());
        assertEquals(booth, saved.getBooth());
    }

    @Test
    void submitLead_existingVisitorLead_updatesContactWithoutResettingQualification() {
        BoothLead existing = BoothLead.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .visitor(visitor)
                .fullName("Tên cũ")
                .email("old@example.com")
                .status(LeadStatus.QUALIFIED)
                .exhibitorNote("Gọi lại vào thứ Hai")
                .consentAt(Instant.now().minusSeconds(3600))
                .createdAt(Instant.now().minusSeconds(3600))
                .build();

        when(exhibitionRepository.findByUuid(exhibition.getUuid())).thenReturn(Optional.of(exhibition));
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibition.getUuid(), booth.getId(), BoothStatus.PUBLISHED)).thenReturn(Optional.of(booth));
        when(boothLeadRepository.findByBoothIdAndVisitorId(booth.getId(), visitor.getId()))
                .thenReturn(Optional.of(existing));
        when(boothLeadRepository.save(existing)).thenReturn(existing);

        var response = boothLeadService.submitLead(
                visitor,
                exhibition.getUuid(),
                booth.getId(),
                new CreateBoothLeadRequest(
                        "Tên mới",
                        "new@example.com",
                        "",
                        "Công ty mới",
                        "Nhu cầu mới",
                        true));

        assertTrue(response.alreadySubmitted());
        assertEquals("Tên mới", existing.getFullName());
        assertEquals("new@example.com", existing.getEmail());
        assertEquals(LeadStatus.QUALIFIED, existing.getStatus());
        assertEquals("Gọi lại vào thứ Hai", existing.getExhibitorNote());
    }

    @Test
    void updateLead_notOwned_returnsNotFoundWithoutLeakingLead() {
        Company company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitor).name("ABC").build();
        UUID leadId = UUID.randomUUID();
        when(companyRepository.findByOwnerUserId(exhibitor.getId())).thenReturn(Optional.of(company));
        when(boothLeadRepository.findOwnedLead(leadId, company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> boothLeadService.updateLead(
                        exhibitor,
                        leadId,
                        new UpdateBoothLeadRequest(LeadStatus.CONTACTED, "Đã gọi")));

        assertEquals(ErrorCode.LEAD_NOT_FOUND, exception.getErrorCode());
    }
}
