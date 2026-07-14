package com.jayant.payment.Sentinel_Ledger.repository;

import com.jayant.payment.Sentinel_Ledger.model.entities.BankEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BankEntryRepository extends JpaRepository<BankEntry, UUID> {

    List<BankEntry> findByTransactionId(UUID transactionId);

    List<BankEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.type = 'DEBIT' THEN -e.amount ELSE e.amount END), 0) " +
            "FROM BankEntry e WHERE e.transaction.id = :transactionId")
    BigDecimal sumNetAmountByTransaction(@Param("transactionId") UUID transactionId);
}