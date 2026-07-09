package com.jayant.payment.Sentinel_Ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayant.payment.Sentinel_Ledger.model.dtos.request.TransactionPaymentRequestDTO;
import com.jayant.payment.Sentinel_Ledger.model.dtos.response.TransactionPaymentResponseDTO;

import java.util.List;
import java.util.UUID;

public interface PaymentService {
    TransactionPaymentResponseDTO processPayment(String idempotencyKey, TransactionPaymentRequestDTO request) throws JsonProcessingException;
    TransactionPaymentResponseDTO getPayment(UUID transactionId);
    List<TransactionPaymentResponseDTO> getAllPayments();
}