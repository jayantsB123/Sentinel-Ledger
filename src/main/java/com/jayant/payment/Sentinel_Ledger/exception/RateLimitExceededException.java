package com.jayant.payment.Sentinel_Ledger.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) { super(message); }
}