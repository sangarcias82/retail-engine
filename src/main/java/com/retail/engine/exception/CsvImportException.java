package com.retail.engine.exception;

public class CsvImportException extends RuntimeException {

    public CsvImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
