package com.example.vex360.features.wallet;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.ExhibitionWalletSummaryDTO;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.enums.WalletTransactionType;
import com.example.vex360.features.wallet.repositories.OrganizerWalletTransactionRepository;
import com.example.vex360.features.wallet.services.OrganizerWalletService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;

@ExtendWith(MockitoExtension.class)
class WalletPerformanceOptimizationTest {

    @Mock
    private ExhibitionService exhibitionService;

    @Mock
    private OrganizerWalletTransactionRepository transactionRepository;

    @Mock
    private CompanyService companyService;

    @InjectMocks
    private OrganizerWalletService walletService;

    private User organizer;

    @BeforeEach
    void setUp() {
        organizer = User.builder().id(UUID.randomUUID()).build();
        Company.builder().id(UUID.randomUUID()).name("Organizer Co").ownerUser(organizer).build();
    }

    @Test
    void getExhibitionSummaries_BatchFetchesTransactionsInSingleQuery() {
        Exhibition ex1 = Exhibition.builder().id(101).uuid(UUID.randomUUID()).name("Exhibition 1")
                .status(ExhibitionStatus.ACTIVE).organizer(organizer).build();
        Exhibition ex2 = Exhibition.builder().id(102).uuid(UUID.randomUUID()).name("Exhibition 2")
                .status(ExhibitionStatus.ACTIVE).organizer(organizer).build();

        Pageable pageable = PageRequest.of(0, 20);
        ExhibitionResponseDTO ex1Dto = ExhibitionResponseDTO.builder()
                .id(ex1.getId()).uuid(ex1.getUuid()).name(ex1.getName()).status("ACTIVE").build();
        ExhibitionResponseDTO ex2Dto = ExhibitionResponseDTO.builder()
                .id(ex2.getId()).uuid(ex2.getUuid()).name(ex2.getName()).status("ACTIVE").build();
        when(exhibitionService.searchExhibitionsForOrganizer(
                organizer, null, null, null, null, null, pageable))
                .thenReturn(PageResponse.<ExhibitionResponseDTO>builder()
                        .content(List.of(ex1Dto, ex2Dto))
                        .page(0)
                        .size(20)
                        .totalElements(2)
                        .totalPages(1)
                        .first(true)
                        .last(true)
                        .build());

        OrganizerWalletTransaction tx1 = OrganizerWalletTransaction.builder()
                .id(1L)
                .exhibition(ex1)
                .type(WalletTransactionType.PAYMENT_CREDIT)
                .amount(new BigDecimal("500000.00"))
                .build();

        OrganizerWalletTransaction tx2 = OrganizerWalletTransaction.builder()
                .id(2L)
                .exhibition(ex2)
                .type(WalletTransactionType.PAYMENT_CREDIT)
                .amount(new BigDecimal("300000.00"))
                .build();

        when(transactionRepository.findByExhibitionIdIn(List.of(101, 102)))
                .thenReturn(List.of(tx1, tx2));

        PageResponse<ExhibitionWalletSummaryDTO> result = walletService.getExhibitionSummariesForOrganizer(organizer,
                pageable);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(new BigDecimal("500000.00"), result.getContent().get(0).getOrganizerNetRevenue());
        assertEquals(new BigDecimal("300000.00"), result.getContent().get(1).getOrganizerNetRevenue());

        // Prove 1 batch query was invoked instead of N separate queries
        verify(transactionRepository, times(1)).findByExhibitionIdIn(List.of(101, 102));
    }
}
