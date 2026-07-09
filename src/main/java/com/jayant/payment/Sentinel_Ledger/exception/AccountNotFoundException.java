package com.jayant.payment.Sentinel_Ledger.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) { super(message); }
}