package com.jayant.payment.Sentinel_Ledger.model.dtos.response;

import com.jayant.payment.Sentinel_Ledger.model.enums.EntryType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EntryResponseDTO(
        UUID id,
        BigDecimal amount,
        EntryType type,
        UUID accountId,
        UUID transactionId,
        Instant createdAt
) {}
