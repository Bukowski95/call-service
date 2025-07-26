package com.onextel.CallServiceApplication.exception;

public class ServiceShuttingDownException extends RuntimeException {
    public ServiceShuttingDownException(String message) {
        super(message);
    }

    public ServiceShuttingDownException(String message, Throwable cause) {
        super(message, cause);
    }
}
