package com.onextel.CallServiceApplication.exception;

public class WebhookNotFoundException extends Exception {

    public WebhookNotFoundException(String message) {
        super(message);
    }

    public WebhookNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
