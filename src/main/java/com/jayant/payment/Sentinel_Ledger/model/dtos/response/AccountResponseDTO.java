package com.jayant.payment.Sentinel_Ledger.model.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponseDTO(
        UUID id,
        String accountNo,
        String name,
        BigDecimal balance,
        String currency,
        Instant createdAt
) {}