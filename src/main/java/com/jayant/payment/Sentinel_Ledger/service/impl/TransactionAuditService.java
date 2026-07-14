package com.jayant.payment.Sentinel_Ledger.service.impl;

import com.jayant.payment.Sentinel_Ledger.exception.AccountNotFoundException;
import com.jayant.payment.Sentinel_Ledger.exception.ConcurrentRequestException;
import com.jayant.payment.Sentinel_Ledger.model.dtos.request.TransactionPaymentRequestDTO;
import com.jayant.payment.Sentinel_Ledger.model.entities.BankTransaction;
import com.jayant.payment.Sentinel_Ledger.model.enums.PaymentStatus;
import com.jayant.payment.Sentinel_Ledger.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionAuditService {

    private final BankTransactionRepository transactionRepository;

    /**
     * Own independent transaction : commits regardless of what the caller does afterward.
     * This is the ONLY place a transaction row gets created, so every request that
     * reaches this point is guaranteed to be logged, no matter the eventual outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankTransaction createProcessingRecord(String idempotencyKey, String fingerprint, TransactionPaymentRequestDTO request) {
        BankTransaction transaction = new BankTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setRequestFingerprint(fingerprint);
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency());
        transaction.setStatus(PaymentStatus.PROCESSING);
        transaction.setDescription(request.description());

        try {
            return transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrentRequestException("A request with this idempotency key is already being processed.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStatus(UUID transactionId, PaymentStatus status) {
        BankTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AccountNotFoundException("Transaction not found: " + transactionId));
        tx.setStatus(status);
        transactionRepository.save(tx);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID transactionId, String responsePayload) {
        BankTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AccountNotFoundException("Transaction not found: " + transactionId));
        tx.setStatus(PaymentStatus.COMPLETED);
        tx.setResponsePayload(responsePayload);
        transactionRepository.save(tx);
    }
}