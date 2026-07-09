package com.jayant.payment.Sentinel_Ledger.controller;

import com.jayant.payment.Sentinel_Ledger.model.dtos.response.AccountStatementResponseDTO;
import com.jayant.payment.Sentinel_Ledger.service.EntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/statement")
@RequiredArgsConstructor
public class EntryController {

    private final EntryService entryService;

    @GetMapping
    public ResponseEntity<AccountStatementResponseDTO> getStatement(@PathVariable UUID accountId) {
        return ResponseEntity.ok(entryService.getAccountStatement(accountId));
    }
}