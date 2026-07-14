package com.jayant.payment.Sentinel_Ledger.service.impl;

import com.jayant.payment.Sentinel_Ledger.exception.AccountNotFoundException;
import com.jayant.payment.Sentinel_Ledger.exception.ConcurrentRequestException;
import com.jayant.payment.Sentinel_Ledger.exception.InsufficientFundsException;
import com.jayant.payment.Sentinel_Ledger.model.dtos.request.TransactionPaymentRequestDTO;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.TransactionPaymentResponseDTO;

import com.jayant.payment.Sentinel_Ledger.model.entities.BankAccount;
import com.jayant.payment.Sentinel_Ledger.model.entities.BankEntry;
import com.jayant.payment.Sentinel_Ledger.model.entities.BankTransaction;
import com.jayant.payment.Sentinel_Ledger.model.enums.EntryType;
import com.jayant.payment.Sentinel_Ledger.model.enums.PaymentStatus;
import com.jayant.payment.Sentinel_Ledger.repository.BankAccountRepository;
import com.jayant.payment.Sentinel_Ledger.repository.BankEntryRepository;
import com.jayant.payment.Sentinel_Ledger.repository.BankTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentExecutionService {

    private final BankTransactionRepository transactionRepository;
    private final BankAccountRepository accountRepository;
    private final BankEntryRepository entryRepository;
    private final TransactionAuditService transactionAuditService;
    private final ObjectMapper objectMapper;


    /**
     * The actual money-movement logic. Runs inside a single DB transaction so that
     * either everything commits (transaction row + both entries + both balance updates)
     * or nothing does : no partial ledger states.
     */
    @Transactional
    public TransactionPaymentResponseDTO executePayment(String idempotencyKey, String fingerprint, TransactionPaymentRequestDTO request) {

        // Create the transaction row FIRST, in PROCESSING state.
        // The idempotency_key UNIQUE constraint at the DB level is our final backstop :
        // even if the Redis lock somehow failed, a duplicate insert here throws and we
        // treat it as a concurrent-request conflict.
        BankTransaction transaction = transactionAuditService.createProcessingRecord(idempotencyKey, fingerprint, request);

        // ---- Lock BOTH accounts in a deterministic order to prevent deadlocks ----
        // If Request A locks (account1, account2) and Request B locks (account2, account1)
        // concurrently, they can deadlock waiting on each other. Always locking in a fixed
        // order (e.g. by UUID comparison)
        try {
            UUID fromId = request.fromAccountId();
            UUID toId = request.toAccountId();

            UUID firstLockId = fromId.compareTo(toId) < 0 ? fromId : toId;
            UUID secondLockId = fromId.compareTo(toId) < 0 ? toId : fromId;

            BankAccount first = accountRepository.findByIdForUpdate(firstLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
            BankAccount second = accountRepository.findByIdForUpdate(secondLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

            BankAccount fromAccount = fromId.equals(first.getId()) ? first : second;
            BankAccount toAccount = toId.equals(first.getId()) ? first : second;

            // ---- Business validation ----
            if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
                throw new InsufficientFundsException("Account " + fromAccount.getAccountNo() + " has insufficient balance.");
            }

            // ---- Move the money ----
            fromAccount.setBalance(fromAccount.getBalance().subtract(request.amount()));
            toAccount.setBalance(toAccount.getBalance().add(request.amount()));

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            // ---- Write the double-entry records ----
            BankEntry debitEntry = new BankEntry();
            debitEntry.setId(UUID.randomUUID());
            debitEntry.setAmount(request.amount());
            debitEntry.setType(EntryType.DEBIT);
            debitEntry.setAccount(fromAccount);
            debitEntry.setTransaction(transaction);

            BankEntry creditEntry = new BankEntry();
            creditEntry.setId(UUID.randomUUID());
            creditEntry.setAmount(request.amount());
            creditEntry.setType(EntryType.CREDIT);
            creditEntry.setAccount(toAccount);
            creditEntry.setTransaction(transaction);

            entryRepository.save(debitEntry);
            entryRepository.save(creditEntry);

            // ---- Finalize transaction ----
            transaction.setStatus(PaymentStatus.COMPLETED);

            TransactionPaymentResponseDTO response = buildResponseFromExisting(transaction);
            transaction.setResponsePayload(serializeResponse(response));
            transactionRepository.save(transaction);

            return response;
        } catch (Exception e) {
            transactionAuditService.markStatus(transaction.getId(), PaymentStatus.FAILED);
            throw e;
        }
    }


    public TransactionPaymentResponseDTO buildResponseFromExisting(BankTransaction transaction) {
        return new TransactionPaymentResponseDTO(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }

    public String serializeResponse(TransactionPaymentResponseDTO response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return null; // don't fail the whole transaction over response caching
        }
    }

    public TransactionPaymentResponseDTO deserializeResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, TransactionPaymentResponseDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize cached payment response", e);
        }
    }
}