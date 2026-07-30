package com.example.vex360.features.wallet.listeners;

import java.math.BigDecimal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExhibitionCompletedWalletListener {

    private final CompanyService companyService;
    private final OrganizerWalletTransactionRepository walletTxRepository;
    private final OrganizerWalletDomainService walletDomainService;

    @EventListener
    @Transactional
    public void handleExhibitionCompleted(ExhibitionCompletedEvent event) {
        if (event == null || event.getExhibition() == null) {
            return;
        }

        Exhibition exhibition = event.getExhibition();
        if (exhibition.getOrganizer() == null || exhibition.getOrganizer().getId() == null) {
            return;
        }

        try {
            companyService.findByOwnerUserId(exhibition.getOrganizer().getId()).ifPresent(company -> {
                BigDecimal credited = walletTxRepository.sumCreditedAmountByExhibitionId(exhibition.getId());
                BigDecimal released = walletTxRepository.sumReleasedAmountByExhibitionId(exhibition.getId());
                BigDecimal reversed = walletTxRepository.sumReversedAmountByExhibitionId(exhibition.getId());
                BigDecimal remainingToRelease = credited.subtract(released).subtract(reversed);
                if (remainingToRelease.compareTo(BigDecimal.ZERO) > 0) {
                    walletDomainService.releasePendingRevenue(
                            company,
                            exhibition,
                            remainingToRelease,
                            "Lifecycle completion release for exhibition ID " + exhibition.getId(),
                            null);
                }
            });
        } catch (Exception ex) {
            log.error("Failed to release pending revenue for completed exhibition {}", exhibition.getId(), ex);
        }
    }
}
