package com.jayant.payment.Sentinel_Ledger.model.dtos.response;

import java.time.Instant;

public record PaymentErrorResponseDTO(
        String errorCode,
        String message,
        Instant timestamp
) {}