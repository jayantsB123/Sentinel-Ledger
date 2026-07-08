package com.jayant.payment.Sentinel_Ledger.repository;


import com.jayant.payment.Sentinel_Ledger.model.enums.PaymentStatus;
import org.hibernate.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByStatus(PaymentStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.createdAt < :cutoff")
    List<Transaction> findStaleTransactions(
            @Param("status") PaymentStatus status,
            @Param("cutoff") Instant cutoff
    );
}