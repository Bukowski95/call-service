package com.onextel.CallServiceApplication.service.webhook;

import com.onextel.CallServiceApplication.dto.WebhookDeliveryResult;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfigWithMetadata;
import com.onextel.CallServiceApplication.model.webhook.WebhookEvent;
import com.onextel.CallServiceApplication.model.webhook.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookManager {
    private final RedisWebhookRegistry registry;
    private final WebhookRetryEngine retryEngine;
    private final ExecutorService webhookExecutor;

    public void shutdown() {
        try {
            // Shutdown scheduler
            this.webhookExecutor.shutdown();
            if (!this.webhookExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.webhookExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.webhookExecutor.shutdownNow();
        }
        log.info("WebhookManager shutdown complete");
    }

    // ========== Webhook Management ========== //
    public CompletableFuture<Void> registerWebhook(String accountId, WebhookConfig config) {
        return CompletableFuture.runAsync(() -> {
            config.setAccountId(accountId);
            registry.registerWebhook(accountId, config).join();
            log.info("Registered webhook for account {}", accountId);
        }, webhookExecutor);
    }

    public CompletableFuture<Void> unregisterWebhook(String accountId, String url) {
        return CompletableFuture.runAsync(() -> {
            registry.unregisterWebhook(accountId, url).join();
            log.info("Unregistered webhook {} for account {}", url, accountId);
        }, webhookExecutor);
    }

    // ========== Query Methods ========== //

    public CompletableFuture<Optional<WebhookConfig>> getWebhookConfig(String accountId, String url) {
        return registry.getWebhookConfig(accountId, url)
                .thenApply(opt -> opt.map(WebhookConfigWithMetadata::getConfig));
    }

    public CompletableFuture<List<WebhookConfig>> getWebhooksForAccount(String accountId) {
        return registry.getConfigsForAccount(accountId);
    }

    public CompletableFuture<List<WebhookConfig>> getWebhooksForEvent(WebhookEventType eventType) {
        return registry.getConfigsForEvent(eventType);
    }

    // ========== Event Delivery ========== //

    public CompletableFuture<Void> deliverEvent(WebhookEvent event) {
        return registry.getConfigsForEvent(event.getType())
                .thenComposeAsync(configs -> {
                    List<CompletableFuture<Void>> deliveries = configs.stream()
                            .filter(config -> shouldDeliver(event, config))
                            .map(config -> deliverWithRetry(event, config))
                            .toList();

                    return CompletableFuture.allOf(deliveries.toArray(new CompletableFuture[0]));
                }, webhookExecutor)
                .exceptionally(ex -> {
                    log.error("Failed to process event delivery", ex);
                    return null;
                });
    }

    private boolean shouldDeliver(WebhookEvent event, WebhookConfig config) {
        return config.isActive() &&
                config.getSubscribedEvents().contains(event.getType());
//        return config.isActive() &&
//                config.getSubscribedEvents().contains(event.getType()) &&
//                (config.getFilterExpression() == null || matchesFilter(event, config));
    }

    private boolean matchesFilter(WebhookEvent event, WebhookConfig config) {
        // Implement actual filter logic
        return true;
    }

    private CompletableFuture<Void> deliverWithRetry(WebhookEvent event, WebhookConfig config) {
        return retryEngine.executeWithRetry(event, config)
                .thenApplyAsync(result -> {
                    registry.touch(config.getAccountId(), config.getUrl());
                    logDeliveryResult(config, result);
                    return result;
                }, webhookExecutor)
                .thenAccept(result -> {}) // Convert to Void
                .exceptionally(ex -> {
                    log.warn("Final delivery failure to {}: {}", config.getUrl(), ex.getMessage());
                    return null;
                });
    }

    private void logDeliveryResult(WebhookConfig config, WebhookDeliveryResult result) {
        if (result.isSuccess()) {
            log.info("Delivered to {} in {}ms [Status: {}]",
                    config.getUrl(),
                    result.getDuration().toMillis(),
                    result.getStatusCode());
        } else {
            log.warn("Failed delivery to {}: {}",
                    config.getUrl(),
                    result.getErrorMessage());
        }
    }
}
