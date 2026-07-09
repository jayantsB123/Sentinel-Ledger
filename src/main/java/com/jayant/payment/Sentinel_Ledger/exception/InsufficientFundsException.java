package com.jayant.payment.Sentinel_Ledger.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) { super(message); }
}