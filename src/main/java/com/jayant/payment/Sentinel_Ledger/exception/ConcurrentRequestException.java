package com.jayant.payment.Sentinel_Ledger.exception;

// Same idempotency key is already being processed RIGHT NOW by another request (lock held)
public class ConcurrentRequestException extends RuntimeException {
    public ConcurrentRequestException(String message) { super(message); }
}