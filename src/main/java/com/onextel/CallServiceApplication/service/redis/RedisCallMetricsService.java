package com.onextel.CallServiceApplication.service.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.onextel.CallServiceApplication.common.JsonUtil;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.stats.CampaignStats;
import com.onextel.CallServiceApplication.model.stats.StandaloneStats;
import com.onextel.CallServiceApplication.model.stats.StateTransition;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisCallMetricsService {
    //operation times
    private final ConcurrentHashMap<String, Timer> operationTimers = new ConcurrentHashMap<>();
    private final RedisConnectionPool connectionPool;
    private final CallStateBatchUpdater batchUpdater;
    private final MeterRegistry meterRegistry;

    /**
     * Records operation duration with nanosecond precision
     * @param operationName Logical name of the operation (e.g., "call.recovery")
     * @param duration Duration in nanoseconds
     */
    public void recordOperationDuration(String operationName, long duration) {
        timer(operationName).record(duration, TimeUnit.NANOSECONDS);
    }

    /**
     * Times a Runnable operation
     * @param operationName Logical name of the operation
     * @param operation The code to time and execute
     */
    public void timeOperation(String operationName, Runnable operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            operation.run();
        } finally {
            sample.stop(timer(operationName));
        }
    }

    /**
     * Times a Supplier operation and returns its value
     * @param operationName Logical name of the operation
     * @param operation The code to time and execute
     * @return The result of the operation
     */
    public <T> T timeOperation(String operationName, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return operation.get();
        } finally {
            sample.stop(timer(operationName));
        }
    }

    private Timer timer(String name) {
        return operationTimers.computeIfAbsent(name,
                n -> Timer.builder("operation.duration")
                        .tag("operation", n)
                        .publishPercentiles(0.5, 0.95, 0.99) // 50th, 95th, 99th percentiles
                        .register(meterRegistry));
    }

    /**
     * ========== CALL METRICS ==========
     */

    public Map<String, String> getGlobalStats() {
        return connectionPool.executeSync("getGlobalStats", connection ->
                connection.sync().hgetall(RedisKeys.GLOBAL_STATS_KEY)
        );
    }

    public CompletableFuture<CallState> getCurrentState(String callUuid) {
        return connectionPool.executeAsyncCommand(commands ->
                commands.get(RedisKeys.callStateKey(callUuid))
                        .thenApply(state -> state != null ? CallState.valueOf(state) : null));
    }

    public CompletableFuture<List<StateTransition>> getStateHistory(String callUuid) {
        return connectionPool.executeAsyncCommand(commands ->
                commands.lrange(RedisKeys.stateHistoryKey(callUuid), 0, -1)
                        .thenApply(entries -> entries.stream()
                                .map(json -> {
                                    try {
                                        return JsonUtil.deserialize(json, StateTransition.class);
                                    } catch (JsonProcessingException e) {
                                        throw new CompletionException(e);
                                    }
                                })
                                .collect(Collectors.toList())
                        ));
    }

    public CompletableFuture<Void> updateCallState(String callUuid, CallState newState,
                                                   String campaignId, String instanceId) {
        return updateCallStateInternal(callUuid, newState, campaignId, instanceId, false);
    }

    public CompletableFuture<Void> updateCallStateImmediate(String callUuid, CallState newState) {
        return updateCallStateInternal(callUuid, newState, null, null, true);
    }

    private CompletableFuture<Void> updateCallStateInternal(String callUuid, CallState newState,
                                                            String campaignId, String instanceId,
                                                            boolean immediate) {
        if (immediate) {
            return executeImmediateUpdate(callUuid, newState, campaignId, instanceId);
        } else {
            CompletableFuture<Void> stateFuture = batchUpdater.queueUpdate(callUuid, newState);
            if (campaignId != null && instanceId != null) {
                return stateFuture.thenCompose(__ ->
                        updateCampaignStats(callUuid, newState, campaignId, instanceId)
                );
            }
            return stateFuture;
        }
    }

    private CompletableFuture<Void> executeImmediateUpdate(String callUuid, CallState newState,
                                                           String campaignId, String instanceId) {
        return connectionPool.executeAsyncCommand(commands -> {
            commands.multi();

            // 1. State + History
            String stateKey = RedisKeys.callStateKey(callUuid);
            commands.set(stateKey, newState.name());
            commands.expire(stateKey, RedisKeys.TTL.STATE_SECONDS);

            StateTransition transition = new StateTransition(
                    newState, System.currentTimeMillis(), "system"
            );
            try {
                commands.rpush(
                        RedisKeys.stateHistoryKey(callUuid),
                        JsonUtil.serialize(transition)
                );
                commands.expire(
                        RedisKeys.stateHistoryKey(callUuid),
                        RedisKeys.TTL.STATE_SECONDS
                );
            } catch (JsonProcessingException e) {
                log.error("State transition serialization failed", e);
            }

            // 2. Active calls
            if (newState.isActive()) {
                commands.zadd(
                        RedisKeys.ACTIVE_CALLS_ZSET,
                        System.currentTimeMillis(),
                        callUuid
                );
            } else if (newState.isTerminal()) {
                commands.zrem(RedisKeys.ACTIVE_CALLS_ZSET, callUuid);
            }

            // 3. Statistics
            if (campaignId != null && instanceId != null) {
                updateStatistics(commands, null, newState, campaignId, instanceId);
            } else {
                updateStandaloneStats(commands, null, newState);
            }

            return commands.exec().thenApply(__ -> null);
        });
    }

    private CompletableFuture<Void> updateCampaignStats(String callUuid, CallState newState,
                                                        String campaignId, String instanceId) {
        return connectionPool.executeAsyncCommand(commands -> {
            commands.multi();
            updateStatistics(commands, null, newState, campaignId, instanceId);
            return commands.exec().thenApply(__ -> null);
        });
    }

    private void updateStatistics(RedisAsyncCommands<String, String> commands,
                                  String currentState, CallState newState,
                                  String campaignId, String instanceId) {
        String instanceKey = RedisKeys.campaignKey(campaignId) + ":" + instanceId;
        String campaignKey = RedisKeys.campaignKey(campaignId) + ":total";

        updateStatsForKey(commands, currentState, newState, instanceKey);
        updateStatsForKey(commands, currentState, newState, campaignKey);
    }

    private void updateStandaloneStats(RedisAsyncCommands<String, String> commands,
                                       String currentState, CallState newState) {
        updateStatsForKey(commands, currentState, newState, RedisKeys.STANDALONE_STATS_KEY);
    }

    private void updateStatsForKey(RedisAsyncCommands<String, String> commands,
                                   String currentState, CallState newState,
                                   String statsKey) {
        if (currentState != null) {
            commands.hincrby(statsKey, "count:" + currentState, -1);
        }
        commands.hincrby(statsKey, "count:" + newState.name(), 1);

        if (newState.isTerminal()) {
            commands.hincrby(statsKey,
                    newState == CallState.ENDED ? "completed" : "failed",
                    1
            );
        }
        commands.expire(statsKey, RedisKeys.TTL.STATE_SECONDS);
    }

    public CompletableFuture<CampaignStats> getCampaignStats(String campaignId) {
        return getStats(RedisKeys.campaignKey(campaignId) + ":total")
                .thenApply(map -> new CampaignStats(campaignId, null, map));
    }

    public CompletableFuture<CampaignStats> getCampaignInstanceStats(String campaignId, String instanceId) {
        return getStats(RedisKeys.campaignKey(campaignId) + ":" + instanceId)
                .thenApply(map -> new CampaignStats(campaignId, instanceId, map));
    }

    public CompletableFuture<StandaloneStats> getStandaloneStats() {
        return getStats(RedisKeys.STANDALONE_STATS_KEY)
                .thenApply(StandaloneStats::new);
    }

    private CompletableFuture<Map<String, String>> getStats(String key) {
        return connectionPool.executeAsyncCommand(commands ->
                commands.hgetall(key)
                        .thenApply(map -> map != null ? map : Collections.emptyMap())
        );
    }
}