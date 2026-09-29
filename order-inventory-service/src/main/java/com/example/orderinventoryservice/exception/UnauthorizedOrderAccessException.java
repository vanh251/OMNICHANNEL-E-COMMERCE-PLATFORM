package com.example.orderinventoryservice.exception;

public class UnauthorizedOrderAccessException extends RuntimeException {
    public UnauthorizedOrderAccessException(String message) {
        super(message);
    }
}
