package com.example.identityservice.exception;

public class VerificationRateLimitException extends RuntimeException {
    public VerificationRateLimitException() { super("Too many verification emails requested"); }
}
