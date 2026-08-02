package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignRequestCommunicationServiceAdditionalUnitTest {

    @Mock
    private DesignRequestRepository requestRepository;
    @Mock
    private DesignRequestMessageRepository messageRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DesignRequestCommunicationService service;
    private User designer;
    private DesignRequest request;

    @BeforeEach
    void setUp() {
        service = new DesignRequestCommunicationService(
                requestRepository, messageRepository, companyService, eventPublisher);
        designer = User.builder().id(UUID.randomUUID()).fullName("Designer").build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(UUID.randomUUID()).build())
                .assignedDesigner(designer)
                .build();
    }

    @Test
    void sendForDesignerTrimsAndPersistsMessage() {
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(messageRepository.save(any(DesignRequestMessage.class))).thenAnswer(invocation -> {
            DesignRequestMessage message = invocation.getArgument(0);
            return DesignRequestMessage.builder()
                    .id(UUID.randomUUID())
                    .designRequest(message.getDesignRequest())
                    .sender(message.getSender())
                    .message(message.getMessage())
                    .createdAt(Instant.now())
                    .build();
        });

        DesignRequestMessageResponseDTO response = service.sendForDesigner(
                designer, request.getId(), "  Please adjust the logo  ");

        assertEquals("Please adjust the logo", response.getMessage());
        ArgumentCaptor<DesignRequestMessage> captor = ArgumentCaptor.forClass(DesignRequestMessage.class);
        verify(messageRepository).save(captor.capture());
        assertEquals("Please adjust the logo", captor.getValue().getMessage());
        assertSame(designer, captor.getValue().getSender());
        verify(eventPublisher).publishEvent(any(DesignRequestMessageCreatedEvent.class));
    }

    @Test
    void sendForDesignerRejectsUnauthenticatedUser() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.sendForDesigner(null, request.getId(), "Message"));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        verify(requestRepository, never()).findById(any());
    }

    @Test
    void sendForDesignerRejectsDifferentDesigner() {
        User anotherDesigner = User.builder().id(UUID.randomUUID()).build();
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.sendForDesigner(anotherDesigner, request.getId(), "Message"));

        assertSame(ErrorCode.DESIGN_MESSAGE_NOT_ALLOWED, exception.getErrorCode());
        verify(messageRepository, never()).save(any());
    }
}
