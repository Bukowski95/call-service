package com.onextel.CallServiceApplication.service.webhook;

import com.onextel.CallServiceApplication.model.webhook.QueuedWebhook;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookEvent;

import java.time.Instant;
import java.util.Optional;

public interface WebhookEventQueue {
    void enqueue(WebhookEvent event, WebhookConfig config, Instant executeAt);
    Optional<QueuedWebhook> peek();
    void remove(QueuedWebhook queuedWebhook);
    void scheduleRetry(WebhookEvent event, WebhookConfig config, int retryCount);
}