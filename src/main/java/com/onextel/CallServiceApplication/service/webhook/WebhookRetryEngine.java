package com.onextel.CallServiceApplication.service.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.onextel.CallServiceApplication.common.JsonUtil;
import com.onextel.CallServiceApplication.dto.WebhookDeliveryResult;
import com.onextel.CallServiceApplication.exception.WebhookDeliveryException;
import com.onextel.CallServiceApplication.exception.WebhookSerializationException;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookRetryEngine {
    private final WebhookDeliveryService deliveryService;
    private final WebhookEventQueue eventQueue;
    private final RetryTemplate retryTemplate;
    private final RedisWebhookDeliveryLog deliveryLog;

    @PostConstruct
    public void init() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(30000);

        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));
    }

    public CompletableFuture<WebhookDeliveryResult> executeWithRetry(WebhookEvent event, WebhookConfig config) {
        return CompletableFuture.supplyAsync(() ->
                retryTemplate.execute(context -> {
                    WebhookDeliveryResult result = deliveryService.deliver(
                            config.getUrl(),
                            config.getSecret(),
                            serializeWebhookEvent(event)
                    );
                    if (result.isSuccess()) {
                        deliveryLog.logSuccess(
                                event,
                                config,
                                context.getRetryCount() + 1,
                                result.getDuration()
                        );
                    } else {
                        throw new WebhookDeliveryException("Delivery failed: " + result.getErrorMessage());
                    }

                    return result;
                }, context -> {
                    // Retry exhausted callback - schedule for later
                    deliveryLog.logFailure(
                            event,
                            config,
                            context.getRetryCount() + 1,
                            "Max retries exceeded"
                    );
                    eventQueue.scheduleRetry(event, config, context.getRetryCount());
                    return WebhookDeliveryResult.failure("Queued for retry");
                })
        );
    }

    private String serializeWebhookEvent(WebhookEvent event) {
        try {
            return JsonUtil.serialize(event);
        } catch (JsonProcessingException e) {
            throw new WebhookSerializationException("Failed to serialize webhook event", e);
        }
    }

    private WebhookEvent deserializeWebhookEvent(String json) {
        try {
            return JsonUtil.deserialize(json, WebhookEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize WebhookEvent", e);
            throw new WebhookSerializationException("Failed to deserialize webhook event", e);
        }
    }

}
