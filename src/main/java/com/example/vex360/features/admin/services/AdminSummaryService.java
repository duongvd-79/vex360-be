package com.example.vex360.features.admin.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.admin.dtos.response.AdminSummaryResponseDTO;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.partnership.services.PartnershipRequestService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminSummaryService {

    private final PartnershipRequestService partnershipRequestService;
    private final DesignRequestService designRequestService;
    private final ExhibitionService exhibitionService;

    @Transactional(readOnly = true)
    public AdminSummaryResponseDTO getSummary() {
        return new AdminSummaryResponseDTO(
                partnershipRequestService.countPendingRequests(),
                designRequestService.countPendingRequests(),
                exhibitionService.countPendingExhibitions());
    }
}
