package com.jayant.payment.Sentinel_Ledger.service;

import com.jayant.payment.Sentinel_Ledger.model.dtos.response.AccountStatementResponseDTO;

import java.util.UUID;

public interface EntryService {
    AccountStatementResponseDTO getAccountStatement(UUID accountId);
}