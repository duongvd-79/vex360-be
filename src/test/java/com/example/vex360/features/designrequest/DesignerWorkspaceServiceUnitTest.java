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
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignerWorkspaceServiceUnitTest {
    @Mock DesignRequestRepository designRequestRepository;
    @Mock BoothMapper boothMapper;
    @Mock ProductService productService;
    @Mock BoothDesignService boothDesignService;

    private DesignerWorkspaceService service;
    private User designer;
    private DesignRequest request;

    @BeforeEach
    void setup() {
        service = new DesignerWorkspaceService(
                designRequestRepository,
                boothMapper,
                productService,
                boothDesignService);
        designer = User.builder().id(UUID.randomUUID()).build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
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
