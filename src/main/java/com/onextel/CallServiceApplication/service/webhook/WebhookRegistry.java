package com.onextel.CallServiceApplication.service.webhook;

import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;

import java.util.concurrent.CompletableFuture;

public interface WebhookRegistry {
    CompletableFuture<Void> registerWebhook(String accountId, WebhookConfig config);
    CompletableFuture<Void> updateWebhook(String accountId, WebhookConfig config);
    CompletableFuture<Void>  unregisterWebhook(String accountId, String url);


}
