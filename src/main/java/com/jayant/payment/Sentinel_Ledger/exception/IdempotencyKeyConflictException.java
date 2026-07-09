package com.jayant.payment.Sentinel_Ledger.exception;

// Same idempotency key reused with a DIFFERENT request payload — client bug or replay attack
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) { super(message); }
}