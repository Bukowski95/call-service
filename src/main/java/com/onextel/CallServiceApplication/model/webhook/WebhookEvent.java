package com.onextel.CallServiceApplication.model.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {
    private String accountId;
    private WebhookEventType type;
    private Instant timestamp;
    private Object payload;
}