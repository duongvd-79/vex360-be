package com.example.vex360.features.wallet;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.listeners.ExhibitionCompletedWalletListener;
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ExhibitionCompletedWalletListenerUnitTest {
    @Mock
    CompanyService companyService;
    @Mock
    OrganizerWalletDomainService walletDomainService;

    private ExhibitionCompletedWalletListener listener;
    private Exhibition exhibition;
    private User organizer;

    @BeforeEach
    void setup() {
        listener = new ExhibitionCompletedWalletListener(companyService, walletDomainService);
        organizer = User.builder().id(UUID.randomUUID()).build();
        exhibition = Exhibition.builder().id(1).organizer(organizer).build();
    }

    @Test
    void missingOrganizerCompanyFailsLifecycleTransaction() {
        when(companyService.findByOwnerUserId(organizer.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> listener.handleExhibitionCompleted(new ExhibitionCompletedEvent(this, exhibition)));

        assertSame(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletDomainService);
    }

    @Test
    void delegatesReleaseToWalletService() {
        Company company = company();
        when(companyService.findByOwnerUserId(organizer.getId())).thenReturn(Optional.of(company));

        listener.handleExhibitionCompleted(new ExhibitionCompletedEvent(this, exhibition));

        verify(walletDomainService).releaseCompletedExhibitionRevenue(company, exhibition);
    }

    @Test
    void walletFailurePropagatesForSchedulerRetry() {
        Company company = company();
        AppException failure = new AppException(ErrorCode.WALLET_NOT_FOUND);
        when(companyService.findByOwnerUserId(organizer.getId())).thenReturn(Optional.of(company));
        doThrow(failure).when(walletDomainService).releaseCompletedExhibitionRevenue(company, exhibition);

        AppException thrown = assertThrows(AppException.class,
                () -> listener.handleExhibitionCompleted(new ExhibitionCompletedEvent(this, exhibition)));

        assertSame(failure, thrown);
    }

    private Company company() {
        return Company.builder().id(UUID.randomUUID()).ownerUser(organizer).build();
    }
}
