package com.example.vex360.features.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.admin.dtos.response.AdminSummaryResponseDTO;
import com.example.vex360.features.admin.services.AdminSummaryService;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.partnership.services.PartnershipRequestService;

@ExtendWith(MockitoExtension.class)
class AdminSummaryServiceUnitTest {

    @Mock
    private PartnershipRequestService partnershipRequestService;

    @Mock
    private DesignRequestService designRequestService;

    @Mock
    private ExhibitionService exhibitionService;

    @InjectMocks
    private AdminSummaryService adminSummaryService;

    @Test
    void getSummaryReturnsPendingCounts() {
        when(partnershipRequestService.countPendingRequests()).thenReturn(4L);
        when(designRequestService.countPendingRequests()).thenReturn(7L);
        when(exhibitionService.countPendingExhibitions()).thenReturn(2L);

        AdminSummaryResponseDTO summary = adminSummaryService.getSummary();

        assertEquals(4L, summary.getPendingPartnershipRequests());
        assertEquals(7L, summary.getPendingDesignRequests());
        assertEquals(2L, summary.getPendingExhibitionRequests());
        verify(partnershipRequestService).countPendingRequests();
        verify(designRequestService).countPendingRequests();
        verify(exhibitionService).countPendingExhibitions();
    }

    @Test
    void getSummaryReturnsZeroCounts() {
        AdminSummaryResponseDTO summary = adminSummaryService.getSummary();

        assertEquals(0L, summary.getPendingPartnershipRequests());
        assertEquals(0L, summary.getPendingDesignRequests());
        assertEquals(0L, summary.getPendingExhibitionRequests());
    }
}
