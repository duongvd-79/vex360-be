package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.services.DesignRequestEligibilityService;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignerWorkspaceServiceUnitTest {
    @Mock
    DesignRequestRepository designRequestRepository;
    @Mock
    BoothMapper boothMapper;
    @Mock
    DesignRequestProductRepository requestProductRepository;
    @Mock
    ProductMapper productMapper;
    @Mock
    BoothDesignService boothDesignService;
    @Mock
    CompanyService companyService;
    @Mock
    CompanyStorageService storageService;
    @Mock
    DesignRequestEligibilityService eligibilityService;
    @Mock
    DesignDraftBenefitGuardService benefitGuardService;

    private DesignerWorkspaceService service;
    private User designer;
    private DesignRequest request;

    @BeforeEach
    void setup() {
        service = new DesignerWorkspaceService(
                designRequestRepository,
                boothMapper,
                requestProductRepository,
                productMapper,
                boothDesignService,
                companyService,
                storageService,
                eligibilityService,
                benefitGuardService);
        designer = User.builder().id(UUID.randomUUID()).build();
        Company company = Company.builder()
                .id(UUID.randomUUID())
                .storageUsedBytes(0L)
                .storageReservedBytes(0L)
                .storageQuotaBytes(100L)
                .build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .booth(Booth.builder().id(UUID.randomUUID()).name("Booth").build())
                .assignedDesigner(designer)
                .status(DesignRequestStatus.ASSIGNED)
                .build();
    }

    @Test
    void getWorkspaceReturnsAssignedRequestAndBooth() {
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        DesignerWorkspaceResponseDTO response = service.getWorkspace(designer, request.getId());

        assertEquals(request.getId(), response.getRequestId());
        assertEquals(DesignRequestStatus.ASSIGNED, response.getStatus());
    }

    @Test
    void getWorkspaceRejectsAnotherDesigner() {
        User anotherDesigner = User.builder().id(UUID.randomUUID()).build();
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getWorkspace(anotherDesigner, request.getId()));

        assertSame(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }
}
