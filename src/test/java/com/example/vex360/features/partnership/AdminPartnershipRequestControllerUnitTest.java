package com.example.vex360.features.partnership;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.features.partnership.controllers.AdminPartnershipRequestController;
import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminPartnershipRequestControllerUnitTest {
    @Mock
    private PartnershipRequestService partnershipRequestService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID requestId;
    private PartnershipRequestResponseDTO response;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminPartnershipRequestController(partnershipRequestService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
        requestId = UUID.randomUUID();
        response = response("PENDING");
    }

    @Test
    void getRequestsReturnsPagedApiResponse() throws Exception {
        PageResponse<PartnershipRequestResponseDTO> pageResponse = PageResponse.<PartnershipRequestResponseDTO>builder()
                .content(List.of(response))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(partnershipRequestService.getRequests(
                eq(PartnershipRequestStatus.PENDING),
                eq(Role.EXHIBITOR),
                any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/partnership-requests?status=PENDING&requestedRole=EXHIBITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    void getRequestByIdReturnsApiResponse() throws Exception {
        when(partnershipRequestService.getRequestById(requestId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/partnership-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(requestId.toString()));
    }

    @Test
    void approveRequestReturnsApiResponse() throws Exception {
        PartnershipRequestResponseDTO approvedResponse = response("APPROVED");

        when(partnershipRequestService.approveRequest(requestId)).thenReturn(approvedResponse);

        mockMvc.perform(post("/api/v1/admin/partnership-requests/{id}/approve", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void rejectRequestReturnsApiResponse() throws Exception {
        PartnershipRequestResponseDTO rejectedResponse = response("REJECTED");
        rejectedResponse.setReviewNote("Not a fit");

        when(partnershipRequestService.rejectRequest(eq(requestId), any(RejectPartnershipRequest.class)))
                .thenReturn(rejectedResponse);

        mockMvc.perform(post("/api/v1/admin/partnership-requests/{id}/reject", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RejectPartnershipRequest("Not a fit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewNote").value("Not a fit"));
    }

    private PartnershipRequestResponseDTO response(String status) {
        return new PartnershipRequestResponseDTO(
                requestId,
                null,
                null,
                "Requester Name",
                "requester@example.com",
                "0912345678",
                "Vex360 Partner",
                "EXHIBITOR",
                "We want to partner",
                true,
                status,
                null,
                null,
                null);
    }
}
