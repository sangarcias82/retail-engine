package com.retail.engine.exception;

import org.springframework.http.HttpStatus;

public class PurchaseException extends RuntimeException {

    private final HttpStatus status;

    public PurchaseException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
