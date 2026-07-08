package com.jayant.payment.Sentinel_Ledger.model.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record CreateAccountRequestDTO(

        @NotBlank(message = "Account holder name is required")
        String name,

        @NotNull(message = "Initial balance is required")
        @DecimalMin(value = "0.0", message = "Initial balance cannot be negative")
        BigDecimal initialBalance,

        @NotBlank(message = "Currency is required")
        String currency
) {}