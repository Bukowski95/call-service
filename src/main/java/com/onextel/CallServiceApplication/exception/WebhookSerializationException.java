package com.onextel.CallServiceApplication.exception;

public class WebhookSerializationException extends RuntimeException {
    public WebhookSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}