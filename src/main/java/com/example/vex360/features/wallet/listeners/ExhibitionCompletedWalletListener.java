package com.example.vex360.features.wallet.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExhibitionCompletedWalletListener {

    private final CompanyService companyService;
    private final OrganizerWalletDomainService walletDomainService;

    @EventListener
    @Transactional
    public void handleExhibitionCompleted(ExhibitionCompletedEvent event) {
        if (event == null || event.getExhibition() == null) {
            throw new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
        }

        Exhibition exhibition = event.getExhibition();
        if (exhibition.getOrganizer() == null || exhibition.getOrganizer().getId() == null) {
            throw new AppException(ErrorCode.COMPANY_NOT_FOUND);
        }
        walletDomainService.releaseCompletedExhibitionRevenue(
                companyService.findByOwnerUserId(exhibition.getOrganizer().getId())
                        .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND)),
                exhibition);
    }
}
