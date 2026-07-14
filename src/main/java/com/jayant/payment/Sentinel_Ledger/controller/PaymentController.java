package com.jayant.payment.Sentinel_Ledger.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayant.payment.Sentinel_Ledger.model.dtos.request.TransactionPaymentRequestDTO;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.TransactionPaymentResponseDTO;
import com.jayant.payment.Sentinel_Ledger.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<TransactionPaymentResponseDTO> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionPaymentRequestDTO request) throws JsonProcessingException {

        TransactionPaymentResponseDTO response = paymentService.processPayment(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionPaymentResponseDTO> getPayment(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(paymentService.getPayment(transactionId));
    }

    @GetMapping
    public ResponseEntity<java.util.List<TransactionPaymentResponseDTO>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }
}