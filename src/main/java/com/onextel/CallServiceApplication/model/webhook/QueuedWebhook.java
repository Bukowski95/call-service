package com.onextel.CallServiceApplication.model.webhook;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueuedWebhook implements Serializable {
    private WebhookEvent event;
    private WebhookConfig config;
    private Instant executeAt;

    // For Redis sorted set ordering
    public long getScore() {
        return executeAt.toEpochMilli();
    }
}