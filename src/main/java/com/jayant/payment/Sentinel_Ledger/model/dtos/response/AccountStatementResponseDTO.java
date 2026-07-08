package com.jayant.payment.Sentinel_Ledger.model.dtos.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AccountStatementResponseDTO(
        UUID accountId,
        String accountNo,
        BigDecimal currentBalance,
        List<EntryResponseDTO> entries
) {}