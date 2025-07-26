package com.onextel.CallServiceApplication.exception;

public class CallNotFoundException extends RuntimeException {

    public CallNotFoundException(String message) {
        super(message);
    }

    public CallNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
