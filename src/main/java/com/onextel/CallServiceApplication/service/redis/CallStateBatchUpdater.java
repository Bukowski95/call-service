package com.onextel.CallServiceApplication.service.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.onextel.CallServiceApplication.common.JsonUtil;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.stats.StateTransition;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class CallStateBatchUpdater implements DisposableBean {
    private final RedisConnectionPool connectionPool;
    private final BlockingQueue<StateUpdate> updateQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    @Value("${app.redis.callstate.batch.max-size:500}")
    private int maxBatchSize;

    @Value("${app.redis.callstate.batch.max-wait-ms:2000}")
    private long maxWaitMillis;

    @PostConstruct
    public void init() {
        scheduler.scheduleWithFixedDelay(
                this::checkForFlush,
                maxWaitMillis, maxWaitMillis/2, TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void destroy() {
        try {
            if (!updateQueue.isEmpty()) {
                log.info("Flushing {} pending updates on shutdown", updateQueue.size());
                flushBatch();
            }

            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            log.info("CallStateBatchUpdater shutdown complete");
        } catch (Exception e) {
            log.error("Error during CallStateBatchUpdater shutdown", e);
        }
    }

    public CompletableFuture<Void> queueUpdate(String callUuid, CallState newState) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        updateQueue.add(new StateUpdate(callUuid, newState, future));
        if (updateQueue.size() >= maxBatchSize) {
            checkForFlush();
        }
        return future;
    }

    private void checkForFlush() {
        if (!updateQueue.isEmpty() && isFlushing.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::flushBatch)
                    .exceptionally(ex -> {
                        log.error("Flush failed", ex);
                        isFlushing.set(false);
                        return null;
                    });
        }
    }

    private void flushBatch() {
        try {
            List<StateUpdate> batch = new ArrayList<>(maxBatchSize);
            StateUpdate first = updateQueue.poll(maxWaitMillis, TimeUnit.MILLISECONDS);

            if (first != null) {
                batch.add(first);
                updateQueue.drainTo(batch, maxBatchSize - 1);
                executeBatchUpdate(batch).join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            isFlushing.set(false);
            if (!updateQueue.isEmpty()) checkForFlush();
        }
    }

    private CompletableFuture<Void> executeBatchUpdate(List<StateUpdate> batch) {
        return connectionPool.executeAsyncCommand(commands -> {
            long now = System.currentTimeMillis();
            commands.multi();

            for (StateUpdate update : batch) {
                // 1. State + History (original logic)
                String stateKey = RedisKeys.callStateKey(update.callUuid);
                commands.set(stateKey, update.newState.name());
                commands.expire(stateKey, RedisKeys.TTL.STATE_SECONDS);

                try {
                    StateTransition transition = new StateTransition(
                            update.newState, now, "system"
                    );
                    commands.rpush(
                            RedisKeys.stateHistoryKey(update.callUuid),
                            JsonUtil.serialize(transition)
                    );
                    commands.expire(
                            RedisKeys.stateHistoryKey(update.callUuid),
                            RedisKeys.TTL.STATE_SECONDS
                    );
                } catch (JsonProcessingException e) {
                    log.error("Batch transition serialization failed", e);
                }

                // 2. Active calls (original logic)
                if (update.newState.isActive()) {
                    commands.zadd(RedisKeys.ACTIVE_CALLS_ZSET, now, update.callUuid);
                } else if (update.newState.isTerminal()) {
                    commands.zrem(RedisKeys.ACTIVE_CALLS_ZSET, update.callUuid);
                }
            }

            return commands.exec()
                    .thenAccept(results -> batch.forEach(u -> u.future.complete(null)))
                    .exceptionally(ex -> {
                        batch.forEach(u -> u.future.completeExceptionally(ex));
                        return null;
                    });
        });
    }

    @Data
    @AllArgsConstructor
    private static class StateUpdate {
        final String callUuid;
        final CallState newState;
        final CompletableFuture<Void> future;
    }
}