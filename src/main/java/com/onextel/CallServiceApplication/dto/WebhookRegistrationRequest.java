package com.onextel.CallServiceApplication.dto;

import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookEventType;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
public class WebhookRegistrationRequest {
    private String accountId;
    private String url;
    private String secret;
    private Set<WebhookEventType> subscribedEvents = EnumSet.noneOf(WebhookEventType.class);
    private Set<CallState> stateChangeSubscriptions = EnumSet.noneOf(CallState.class);
    private int timeoutMs = 3000;
    private int maxRetries = 1;
    private boolean includeFullPayload = false;

    // Validation method
    public void validate() {
        if (accountId == null || accountId.isEmpty()) {
            throw new IllegalArgumentException("accountId is required");
        }

        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL is required");
        }

        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("secret is required");
        }
    }

    public WebhookConfig toWebhookConfig() {
       return WebhookConfig.builder()
               .accountId(accountId)
               .url(url)
               .secret(secret)
               .subscribedEvents(subscribedEvents)
               .stateChangeSubscriptions(stateChangeSubscriptions)
               .timeoutMs(timeoutMs)
               .maxRetries(maxRetries)
               .includeFullPayload(includeFullPayload)
               .build();
    }

}
