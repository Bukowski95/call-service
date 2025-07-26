package com.onextel.CallServiceApplication.common.shutdown;

import com.onextel.CallServiceApplication.freeswitch.FreeSwitchRegistry;
import com.onextel.CallServiceApplication.service.CallManager;
import com.onextel.CallServiceApplication.service.CallService;
import com.onextel.CallServiceApplication.service.EventProcessor;
import com.onextel.CallServiceApplication.service.redis.CallStateBatchUpdater;
import com.onextel.CallServiceApplication.service.redis.RedisCallStateManager;
import com.onextel.CallServiceApplication.service.webhook.InMemoryWebhookEventQueue;
import com.onextel.CallServiceApplication.service.webhook.WebhookManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GracefulShutdownHandler {

    private final CallManager callManager;
    private final RedisCallStateManager redisCallStateManager;
    private final WebhookManager webHookManager;
    private final InMemoryWebhookEventQueue inMemoryWebHookQueue;
    private final FreeSwitchRegistry freeSwitchRegistry;
    private final EventProcessor eventProcessor;
    private final CallStateBatchUpdater callStateBatchUpdater;
    private final CallService callService;

    @EventListener
    public void onContextClosed(ContextClosedEvent event) {
        log.info("ContextClosedEvent received - initiating graceful shutdown");
        shutdownInOrder();
    }

//    @PreDestroy
//    public void preDestroyHook() {
//        log.info("PreDestroy hook triggered - initiating graceful shutdown");
//        shutdownInOrder();
//    }

    private void shutdownInOrder() {
        try {
            log.info("Starting shutdown (initiated by {})",
                    Thread.currentThread().getName());

            eventProcessor.shutdown();
            callManager.shutdown();
            callStateBatchUpdater.destroy();
            freeSwitchRegistry.shutdown();
            inMemoryWebHookQueue.shutdown();
            webHookManager.shutdown();
            redisCallStateManager.shutdown();
            callService.shutdown();

            log.info("Graceful shutdown completed.");
        } catch (Exception e) {
            log.error("Exception during service shutdown", e);
        }
    }
}