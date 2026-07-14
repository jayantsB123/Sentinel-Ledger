package com.jayant.payment.Sentinel_Ledger.service;

import com.jayant.payment.Sentinel_Ledger.model.dtos.request.CreateAccountRequestDTO;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.AccountResponseDTO;

import java.util.List;
import java.util.UUID;

public interface AccountService {
    AccountResponseDTO createAccount(CreateAccountRequestDTO request);
    AccountResponseDTO getAccount(UUID accountId);
    List<AccountResponseDTO> getAllAccounts();
}