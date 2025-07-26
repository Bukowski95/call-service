package com.onextel.CallServiceApplication.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onextel.CallServiceApplication.common.JsonUtil;
import com.onextel.CallServiceApplication.dto.WebhookDeliveryResult;
import com.onextel.CallServiceApplication.model.webhook.QueuedWebhook;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class InMemoryWebhookEventQueue implements WebhookEventQueue {
    private final PriorityBlockingQueue<QueuedWebhook> queue =
            new PriorityBlockingQueue<>(100, Comparator.comparing(QueuedWebhook::getExecuteAt));
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final WebhookDeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::processQueue, 0, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        // Shutdown scheduler
        // TODO: Drain queue before shutdown
        try {
            this.scheduler.shutdown();
            if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                this.scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.scheduler.shutdownNow();
        }
        log.info("InMemoryWebhookEventQueue shutdown complete");
    }

    @Override
    public void enqueue(WebhookEvent event, WebhookConfig config, Instant executeAt) {
        queue.put(new QueuedWebhook(event, config, executeAt));
        log.debug("Enqueued webhook for {}", config.getUrl());
    }

    @Override
    public Optional<QueuedWebhook> peek() {
        return Optional.ofNullable(queue.peek())
                .filter(q -> q.getExecuteAt().isBefore(Instant.now()));
    }

    @Override
    public void remove(QueuedWebhook queuedWebhook) {
        queue.remove(queuedWebhook);
    }

    @Override
    public void scheduleRetry(WebhookEvent event, WebhookConfig config, int retryCount) {
        Instant nextAttempt = Instant.now().plusSeconds((long) Math.pow(2, retryCount));
        enqueue(event, config, nextAttempt);
    }

    private void processQueue() {
        try {
            QueuedWebhook queued = queue.peek();
            while (queued != null && queued.getExecuteAt().isBefore(Instant.now())) {
                queue.poll(); // Remove from queue
                deliver(queued); // Process delivery
                queued = queue.peek();
            }
        } catch (Exception e) {
            log.error("Queue processing error", e);
        }
    }

    private void deliver(QueuedWebhook queuedEvent) {
        try {
            WebhookEvent event = queuedEvent.getEvent();
            WebhookConfig config = queuedEvent.getConfig();
            WebhookDeliveryResult result = deliveryService.deliver(
                    config.getUrl(),
                    config.getSecret(),
                    JsonUtil.serialize(event)
            );
            if (result.isSuccess()) {
                log.info("queued webhook event delivery success: to {} in {}ms [Status: {}]",
                        config.getUrl(),
                        result.getDuration().toMillis(),
                        result.getStatusCode());
            } else {
                log.error("queued webhook event delivery failed: {} ", result.getErrorMessage());
            }
        } catch (Exception ex) {
            log.error("queued webhook event delivery failed: {} ", ex.getMessage(), ex);
        }
    }
}