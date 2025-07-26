package com.onextel.CallServiceApplication.model.webhook;

import lombok.Value;

import java.time.Instant;

@Value
public class WebhookConfigWithMetadata {
    WebhookConfig config;
    Instant createdAt;
    Instant updatedAt;
    String version;
    String eTag;

    public boolean isNotModified(String ifNoneMatch) {
        return ifNoneMatch != null && ifNoneMatch.equals(eTag);
    }
}
