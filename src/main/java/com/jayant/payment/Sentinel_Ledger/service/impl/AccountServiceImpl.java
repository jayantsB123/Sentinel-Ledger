package com.jayant.payment.Sentinel_Ledger.service.impl;

import com.jayant.payment.Sentinel_Ledger.exception.AccountNotFoundException;
import com.jayant.payment.Sentinel_Ledger.model.dtos.request.CreateAccountRequestDTO;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.AccountResponseDTO;
import com.jayant.payment.Sentinel_Ledger.model.entities.BankAccount;
import com.jayant.payment.Sentinel_Ledger.repository.BankAccountRepository;
import com.jayant.payment.Sentinel_Ledger.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final BankAccountRepository accountRepository;

    @Override
    @Transactional
    public AccountResponseDTO createAccount(CreateAccountRequestDTO request) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setAccountNo(generateAccountNumber());
        account.setName(request.name());
        account.setBalance(request.initialBalance());
        account.setCurrency(request.currency());

        BankAccount saved = accountRepository.save(account);
        return toResponse(saved);
    }

    @Override
    public AccountResponseDTO getAccount(UUID accountId) {
        BankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return toResponse(account);
    }

    @Override
    public List<AccountResponseDTO> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private String generateAccountNumber() {
        int random = ThreadLocalRandom.current().nextInt(1000000, 9999999);
        return "ACC" + random;
    }

    private AccountResponseDTO toResponse(BankAccount account) {
        return new AccountResponseDTO(
                account.getId(),
                account.getAccountNo(),
                account.getName(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt()
        );
    }
}