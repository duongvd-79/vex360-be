package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestMessageResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestMessage;
import com.example.vex360.features.designrequest.events.DesignRequestMessageCreatedEvent;
import com.example.vex360.features.designrequest.repositories.DesignRequestMessageRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignRequestCommunicationService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignRequestCommunicationServiceUnitTest {

    @Mock
    private DesignRequestRepository requestRepository;
    @Mock
    private DesignRequestMessageRepository messageRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DesignRequestCommunicationService communicationService;
    private User exhibitor;
    private User designer;
    private Company company;
    private DesignRequest designRequest;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        communicationService = new DesignRequestCommunicationService(
                requestRepository, messageRepository, companyService, eventPublisher);

        requestId = UUID.randomUUID();
        exhibitor = User.builder().id(UUID.randomUUID()).role(Role.EXHIBITOR).fullName("Exhibitor").build();
        designer = User.builder().id(UUID.randomUUID()).role(Role.DESIGNER).fullName("Designer").build();
        company = Company.builder().id(UUID.randomUUID()).name("Test Company").build();

        designRequest = DesignRequest.builder()
                .id(requestId)
                .company(company)
                .assignedDesigner(designer)
                .build();
    }

    @Test
    void getForExhibitor_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));

        DesignRequestMessage message = DesignRequestMessage.builder()
                .id(UUID.randomUUID())
                .designRequest(designRequest)
                .sender(exhibitor)
                .message("Hello designer")
                .createdAt(Instant.now())
                .build();
        when(messageRepository.findByDesignRequestId(requestId, pageable))
                .thenReturn(new PageImpl<>(List.of(message), pageable, 1));

        PageResponse<DesignRequestMessageResponseDTO> response = communicationService.getForExhibitor(exhibitor, requestId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Hello designer", response.getContent().get(0).getMessage());
    }

    @Test
    void getForExhibitor_UnauthorizedCompany_ThrowsAppException() {
        Company otherCompany = Company.builder().id(UUID.randomUUID()).name("Other").build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(otherCompany);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));

        Pageable pageable = PageRequest.of(0, 10);

        AppException ex = assertThrows(AppException.class, () -> communicationService.getForExhibitor(exhibitor, requestId, pageable));
        assertEquals(ErrorCode.DESIGN_MESSAGE_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void getForDesigner_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));

        DesignRequestMessage message = DesignRequestMessage.builder()
                .id(UUID.randomUUID())
                .designRequest(designRequest)
                .sender(designer)
                .message("Hi exhibitor")
                .createdAt(Instant.now())
                .build();
        when(messageRepository.findByDesignRequestId(requestId, pageable))
                .thenReturn(new PageImpl<>(List.of(message), pageable, 1));

        PageResponse<DesignRequestMessageResponseDTO> response = communicationService.getForDesigner(designer, requestId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
    }

    @Test
    void getForDesigner_UnassignedDesigner_ThrowsAppException() {
        User otherDesigner = User.builder().id(UUID.randomUUID()).role(Role.DESIGNER).build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));
        Pageable pageable = PageRequest.of(0, 10);

        AppException ex = assertThrows(AppException.class, () -> communicationService.getForDesigner(otherDesigner, requestId, pageable));
        assertEquals(ErrorCode.DESIGN_MESSAGE_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void getForAdmin_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));
        when(messageRepository.findByDesignRequestId(requestId, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<DesignRequestMessageResponseDTO> response = communicationService.getForAdmin(requestId, pageable);

        assertNotNull(response);
        assertEquals(0, response.getContent().size());
    }

    @Test
    void sendForExhibitor_Success() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));

        DesignRequestMessage message = DesignRequestMessage.builder()
                .id(UUID.randomUUID())
                .designRequest(designRequest)
                .sender(exhibitor)
                .message("Valid message text")
                .createdAt(Instant.now())
                .build();
        when(messageRepository.save(any())).thenReturn(message);

        DesignRequestMessageResponseDTO response = communicationService.sendForExhibitor(exhibitor, requestId, "Valid message text");

        assertNotNull(response);
        assertEquals("Valid message text", response.getMessage());
        verify(eventPublisher).publishEvent(any(DesignRequestMessageCreatedEvent.class));
    }

    @Test
    void sendForDesigner_InvalidText_ThrowsAppException() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));

        assertThrows(AppException.class, () -> communicationService.sendForDesigner(designer, requestId, "  "));
        assertThrows(AppException.class, () -> communicationService.sendForDesigner(designer, requestId, "a".repeat(2001)));
    }
}
