package com.example.vex360.features.wallet.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.enums.WalletTransactionType;

public interface OrganizerWalletTransactionRepository extends JpaRepository<OrganizerWalletTransaction, Long> {

    boolean existsByPaymentIdAndType(Integer paymentId, WalletTransactionType type);

    boolean existsByWithdrawalRequestIdAndType(Long withdrawalRequestId, WalletTransactionType type);

    @Query("SELECT t FROM OrganizerWalletTransaction t WHERE t.company.id = :companyId AND (:type IS NULL OR t.type = :type)")
    Page<OrganizerWalletTransaction> findByCompanyIdAndTypeFilter(
            @Param("companyId") UUID companyId,
            @Param("type") WalletTransactionType type,
            Pageable pageable);

    @Query("SELECT t FROM OrganizerWalletTransaction t WHERE t.company.id = :companyId AND t.exhibition.uuid = :exhibitionUuid")
    Page<OrganizerWalletTransaction> findByCompanyIdAndExhibitionUuid(
            @Param("companyId") UUID companyId,
            @Param("exhibitionUuid") UUID exhibitionUuid,
            Pageable pageable);

    List<OrganizerWalletTransaction> findByWalletId(UUID walletId);

    List<OrganizerWalletTransaction> findByExhibitionIdIn(List<Integer> exhibitionIds);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM OrganizerWalletTransaction t WHERE t.exhibition.id = :exhibitionId AND t.type = com.example.vex360.features.wallet.enums.WalletTransactionType.PAYMENT_CREDIT")
    BigDecimal sumCreditedAmountByExhibitionId(@Param("exhibitionId") Integer exhibitionId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM OrganizerWalletTransaction t WHERE t.exhibition.id = :exhibitionId AND t.type = com.example.vex360.features.wallet.enums.WalletTransactionType.EXHIBITION_RELEASE")
    BigDecimal sumReleasedAmountByExhibitionId(@Param("exhibitionId") Integer exhibitionId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM OrganizerWalletTransaction t WHERE t.exhibition.id = :exhibitionId AND t.type = com.example.vex360.features.wallet.enums.WalletTransactionType.PAYMENT_REVERSAL")
    BigDecimal sumReversedAmountByExhibitionId(@Param("exhibitionId") Integer exhibitionId);

    Optional<OrganizerWalletTransaction> findByPaymentIdAndType(Integer paymentId, WalletTransactionType type);
}
