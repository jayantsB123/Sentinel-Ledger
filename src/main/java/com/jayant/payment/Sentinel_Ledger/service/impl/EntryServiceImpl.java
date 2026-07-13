package com.jayant.payment.Sentinel_Ledger.service.impl;

import com.jayant.payment.Sentinel_Ledger.exception.AccountNotFoundException;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.AccountStatementResponseDTO;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.EntryResponseDTO;
import com.jayant.payment.Sentinel_Ledger.model.entities.BankAccount;
import com.jayant.payment.Sentinel_Ledger.model.entities.BankEntry;
import com.jayant.payment.Sentinel_Ledger.repository.BankAccountRepository;
import com.jayant.payment.Sentinel_Ledger.repository.BankEntryRepository;
import com.jayant.payment.Sentinel_Ledger.service.EntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntryServiceImpl implements EntryService {

    private final BankEntryRepository entryRepository;
    private final BankAccountRepository accountRepository;

    @Override
    public AccountStatementResponseDTO getAccountStatement(UUID accountId) {
        BankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        List<BankEntry> entries = entryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        List<EntryResponseDTO> entryResponses = entries.stream()
                .map(e -> new EntryResponseDTO(
                        e.getId(),
                        e.getAmount(),
                        e.getType(),
                        e.getAccount().getId(),
                        e.getTransaction().getId(),
                        e.getCreatedAt()
                ))
                .toList();

        return new AccountStatementResponseDTO(
                account.getId(),
                account.getAccountNo(),
                account.getBalance(),
                entryResponses
        );
    }
}