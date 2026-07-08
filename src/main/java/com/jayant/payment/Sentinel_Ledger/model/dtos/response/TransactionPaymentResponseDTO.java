package com.jayant.payment.Sentinel_Ledger.model.dtos.response;

import com.jayant.payment.Sentinel_Ledger.model.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionPaymentResponseDTO(
        UUID transactionId,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String description,
        Instant createdAt
) {}