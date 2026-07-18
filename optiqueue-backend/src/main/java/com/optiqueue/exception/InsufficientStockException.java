package com.optiqueue.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {
    public InsufficientStockException(Long productId, int requested, int available) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Product %d: requested %d but only %d in stock".formatted(productId, requested, available));
    }
}
