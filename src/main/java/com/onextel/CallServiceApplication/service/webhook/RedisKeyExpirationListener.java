package com.onextel.CallServiceApplication.service.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.regex.Pattern;

public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(RedisKeyExpirationListener.class);

    private static final Pattern WEBHOOK_KEY_PATTERN = Pattern.compile("^wh:config:.+");

    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (WEBHOOK_KEY_PATTERN.matcher(expiredKey).matches()) {
            handleExpiredWebhook(expiredKey);
        }
    }

    private void handleExpiredWebhook(String key) {
        String accountId = key.split(":")[2];
        LOG.warn("Webhook config expired for account: {}", accountId);
        // TODO: Trigger re-registration logic or notify client for webhook expiry
    }
}
