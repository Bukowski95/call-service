package com.onextel.CallServiceApplication.controller;

import com.onextel.CallServiceApplication.dto.WebhookRegistrationRequest;
import com.onextel.CallServiceApplication.exception.ErrorResponse;
import com.onextel.CallServiceApplication.exception.WebhookNotFoundException;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookEvent;
import com.onextel.CallServiceApplication.model.webhook.WebhookEventType;
import com.onextel.CallServiceApplication.service.webhook.WebhookManager;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@RestController
@RequestMapping("/v1/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    private final WebhookManager webhookManager;

    @PostMapping("/{accountId}")
    public ResponseEntity<?> registerWebhook(
            @PathVariable String accountId,
            @RequestBody WebhookRegistrationRequest registrationRequest
    ) {
        try {
            // validate the request
            registrationRequest.validate();
        } catch (IllegalArgumentException e) {
            // validation failed, return a 400 Bad Request with the error message
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Validation Failed", e.getMessage()));
        }
        webhookManager.registerWebhook(accountId, registrationRequest.toWebhookConfig());
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> unregisterWebhook(
            @PathVariable String accountId,
            @RequestParam String url
    ) {
        webhookManager.unregisterWebhook(accountId, url);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/events")
    public ResponseEntity<Void> handleEvent(@RequestBody WebhookEvent event) {
        webhookManager.deliverEvent(event);
        return ResponseEntity.accepted().build();
    }

    // Get all subscriptions for an account or optionally filter by events
    @GetMapping("/{accountId}/subscriptions")
    public CompletableFuture<ResponseEntity<List<WebhookConfig>>> getSubscriptions(
            @PathVariable @NotBlank String accountId,
            @RequestParam(required = false) WebhookEventType eventType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {

        return webhookManager.getWebhooksForAccount(accountId)
                .thenApply(configs -> {
                    // Filter if eventType specified
                    Stream<WebhookConfig> stream = configs.stream();
                    if (eventType != null) {
                        stream = stream.filter(c -> c.getSubscribedEvents().contains(eventType));
                    }

                    // Calculate accurate total count
                    long totalCount = eventType != null
                            ? configs.stream().filter(c -> c.getSubscribedEvents().contains(eventType)).count()
                            : configs.size();

                    // Pagination
                    List<WebhookConfig> result = stream
                            .skip((long) page * size)
                            .limit(size)
                            .collect(Collectors.toList());

                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                            .header("X-Total-Count", String.valueOf(totalCount))
                            .body(result);
                })
                .exceptionally(ex -> {
                    if (ex instanceof WebhookNotFoundException) {
                        return ResponseEntity.notFound().build();
                    }
                    log.error("Failed to fetch subscriptions for {}", accountId, ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                });
    }

    // Check if specific URL is registered
    @GetMapping("/{accountId}/subscriptions/check")
    public CompletableFuture<Boolean> isUrlSubscribed(
            @PathVariable String accountId,
            @RequestParam String url) {
        return webhookManager.getWebhookConfig(accountId, url)
                .thenApply(Optional::isPresent);
    }



}
