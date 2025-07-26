package com.onextel.CallServiceApplication.dto;

import lombok.Value;

import java.time.Duration;

@Value
public class WebhookDeliveryResult {
    boolean success;
    Integer statusCode;
    String responseBody;
    String errorMessage;
    Duration duration;

    public static WebhookDeliveryResult success(int statusCode, String responseBody, Duration duration) {
        return new WebhookDeliveryResult(true, statusCode, responseBody, null, duration);
    }

    public static WebhookDeliveryResult failure(String errorMessage) {
        return new WebhookDeliveryResult(false, null, null, errorMessage, null);
    }
}