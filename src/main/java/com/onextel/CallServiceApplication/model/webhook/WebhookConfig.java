package com.onextel.CallServiceApplication.model.webhook;

import com.onextel.CallServiceApplication.model.CallState;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookConfig {

    @NotBlank
    private String accountId;

    @NotBlank
    private String url;

    @NotBlank
    private String secret;

    @Builder.Default
    private Set<WebhookEventType> subscribedEvents = EnumSet.noneOf(WebhookEventType.class);

    @Builder.Default
    private Set<CallState> stateChangeSubscriptions = EnumSet.noneOf(CallState.class);

    @Builder.Default
    private boolean includeFullPayload = false;

    @Builder.Default
    private long timeoutMs = 3000;

    @Builder.Default
    private int maxRetries = 3;

    @Builder.Default
    private boolean async = true;

    @Builder.Default
    private boolean active = true;

}