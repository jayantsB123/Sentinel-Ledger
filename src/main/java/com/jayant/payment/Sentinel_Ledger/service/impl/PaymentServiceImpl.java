package com.jayant.payment.Sentinel_Ledger.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayant.payment.Sentinel_Ledger.exception.*;
import com.jayant.payment.Sentinel_Ledger.model.dtos.request.TransactionPaymentRequestDTO;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.TransactionPaymentResponseDTO;
import com.jayant.payment.Sentinel_Ledger.model.entities.BankTransaction;
import com.jayant.payment.Sentinel_Ledger.model.enums.PaymentStatus;
import com.jayant.payment.Sentinel_Ledger.repository.BankTransactionRepository;
import com.jayant.payment.Sentinel_Ledger.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final BankTransactionRepository transactionRepository;
    private final RedisDistributedLockService lockService;
    private final IdempotencyCacheService idempotencyCacheService;
    private final PaymentExecutionService paymentExecutionService;

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    @Override
    public TransactionPaymentResponseDTO processPayment(String idempotencyKey, TransactionPaymentRequestDTO request) throws JsonProcessingException {

        String requestFingerprint = computeFingerprint(request);

        // ---- STEP 1: Cache-first check (fast path, no DB hit) ----
        Optional<IdempotencyCacheService.CachedResult> cachedResponse = idempotencyCacheService.get(idempotencyKey);
        if (cachedResponse.isPresent()) {
            IdempotencyCacheService.CachedResult result = cachedResponse.get();
            validateFingerprintMatch(result.fingerprint(), requestFingerprint);

            if (String.valueOf(PaymentStatus.PROCESSING).equals(result.status())) {
                throw new ConcurrentRequestException("A request with this idempotency key is already being processed.");
            }
            return paymentExecutionService.deserializeResponse(result.responseJson());
        }

        // ---- STEP 2: Acquire distributed lock ----
        // Protects against the race where two requests with the SAME idempotency key
        // arrive at nearly the same instant on different app instances/threads/nodes.
        // Without this, both could pass the "not found" check above and double-process.
        String lockToken = lockService.tryLock(idempotencyKey, LOCK_TTL);
        if (lockToken == null) {
            throw new ConcurrentRequestException("A request with this idempotency key is already being processed. Please retry shortly.");
        }

        try {
            // Re-check cache inside lock : another thread may have JUST written the result
            cachedResponse = idempotencyCacheService.get(idempotencyKey);
            if (cachedResponse.isPresent()) {
                IdempotencyCacheService.CachedResult result = cachedResponse.get();
                validateFingerprintMatch(result.fingerprint(), requestFingerprint);
                return paymentExecutionService.deserializeResponse(result.responseJson());
            }

            // ---- STEP 3: Cache truly cold : fallback to DB (handles Redis restart/eviction case) ----
            BankTransaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                validateFingerprintMatch(existing.getRequestFingerprint(), requestFingerprint);
                TransactionPaymentResponseDTO response = paymentExecutionService.buildResponseFromExisting(existing);
                idempotencyCacheService.putFinal(idempotencyKey, requestFingerprint,
                        existing.getStatus().name(), paymentExecutionService.serializeResponse(response));
                return response;
            }

            // ---- STEP 4: Genuinely new request ----
            idempotencyCacheService.putProcessing(idempotencyKey, requestFingerprint);

            TransactionPaymentResponseDTO response = paymentExecutionService.executePayment(idempotencyKey, requestFingerprint, request);

            idempotencyCacheService.putFinal(idempotencyKey, requestFingerprint,
                    String.valueOf(PaymentStatus.COMPLETED), paymentExecutionService.serializeResponse(response));

            return response;
        } catch (Exception e) {
            // Any other failure (account not found, DB conflict, unexpected error) :
            // evict the PROCESSING entry so a retry isn't permanently blocked for 24h.
            // We evict rather than cache FAILED here because we're not certain this
            // was a clean business-rule failure : could be a transient infra issue,
            // so we want the next attempt to go through the full DB fallback check.
            idempotencyCacheService.evict(idempotencyKey);
            throw e;
        } finally {
            lockService.unlock(idempotencyKey, lockToken);
        }
    }

    @Override
    public TransactionPaymentResponseDTO getPayment(UUID transactionId) {
        BankTransaction bankTransaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AccountNotFoundException("Transaction not found: " + transactionId));
        return paymentExecutionService.buildResponseFromExisting(bankTransaction);
    }

    @Override
    public List<TransactionPaymentResponseDTO> getAllPayments() {
        return transactionRepository.findAll().stream()
                .map(paymentExecutionService::buildResponseFromExisting)
                .toList();
    }

    private void validateFingerprintMatch(String existing, String incomingFingerprint) {
        if (!existing.equals(incomingFingerprint)) {
            throw new IdempotencyKeyConflictException("This idempotency key was already used with a different request payload.");
        }
    }


    /**
     * SHA-256 hash of the meaningful request fields. Used to detect if the same
     * idempotency key is reused with a genuinely different payload (client bug or misuse).
     */
    private String computeFingerprint(TransactionPaymentRequestDTO request) {
        try {
            String raw = request.fromAccountId() + "|" + request.toAccountId() + "|" + request.amount().toPlainString() + "|" + request.currency();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes());
            return new String(Hex.encode(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}