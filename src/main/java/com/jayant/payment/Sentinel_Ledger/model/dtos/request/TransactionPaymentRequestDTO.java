package com.jayant.payment.Sentinel_Ledger.model.dtos.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.UUID;

public record TransactionPaymentRequestDTO(

        @NotNull(message = "Source account is required")
        UUID fromAccountId,

        @NotNull(message = "Destination account is required")
        UUID toAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency,

        String description
) {}