package com.example.orderinventoryservice.exception;

public class InvalidStockAdjustmentException extends RuntimeException {
    public InvalidStockAdjustmentException(String sku) {
        super("Stock adjustment exceeds available stock or maximum stock for SKU: " + sku);
    }
}
