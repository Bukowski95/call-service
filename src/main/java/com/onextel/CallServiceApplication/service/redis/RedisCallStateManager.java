package com.onextel.CallServiceApplication.service.redis;

import com.onextel.CallServiceApplication.model.Call;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.Channel;
import com.onextel.CallServiceApplication.model.DTMFEvent;
import com.onextel.CallServiceApplication.util.HostNameProvider;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Facade class for managing application instance and call states with statistics in redis.
 */
@Service
@Slf4j
public class RedisCallStateManager {
    private final GenericObjectPool<StatefulRedisConnection<String, String>> redisLettucePool;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final RedisInstanceManager instanceManager;
    private final RedisCallRegistry callRegistry;
    private final RedisCallMetricsService metricsService;
    private String instanceId;

    public RedisCallStateManager(
            @Qualifier("redisLettuceConnectionPool") GenericObjectPool<StatefulRedisConnection<String, String>> redisLettucePool,
            RedisInstanceManager instanceManager,
            RedisCallRegistry callRegistry,
            RedisCallMetricsService metricsService) {
        this.redisLettucePool = Objects.requireNonNull(redisLettucePool, "Redis connection pool cannot be null");
        this.instanceManager = Objects.requireNonNull(instanceManager, "RedisInstanceManager cannot be null");
        this.callRegistry = Objects.requireNonNull(callRegistry, "RedisCallRegistry cannot be null");
        this.metricsService = Objects.requireNonNull(metricsService, "RedisMetricsService cannot be null");
        log.info("RedisCallStateManager initialized successfully");
    }

    public void shutdown() {
        try {
            if (redisLettucePool != null && !redisLettucePool.isClosed()) {
                redisLettucePool.close();
                log.info("Redis connection pool closed successfully");
            }
            log.info("RedisCallStateManager shutdown complete");
        } catch (Exception e) {
            log.error("Error closing Redis connection pool", e);
        }
    }

    /**
     * ========== INSTANCE MANAGEMENT ==========
     */

    public synchronized void setAppInstanceId(String appInstanceId) {
        if (isInitialized.compareAndSet(false, true)) {
            this.instanceId = appInstanceId;
            instanceManager.registerInstance(appInstanceId, HostNameProvider.getHostname(), 8080, "1");
            callRegistry.setAppInstanceId(appInstanceId);
        } else {
            log.warn("App instance ID already set for RedisCallStateManager");
        }
    }

    public void transferCallsToOrphanPool(String instanceId) {
        instanceManager.transferCallsToOrphanPool(instanceId);
    }

    public void markInstanceAsDown(String appInstanceId) {
        instanceManager.markInstanceAsDown(appInstanceId);
    }

    public void registerInstance(String instanceId, String hostName, int port, String version) {
        instanceManager.registerInstance(instanceId, hostName, port, version);
    }

    public void unregisterInstance() {
        instanceManager.unregisterInstance();
    }

    public boolean acquireLock(String lockKey, int ttlSeconds) {
        return instanceManager.acquireLock(lockKey, ttlSeconds);
    }

    public void releaseLock(String lockKey) {
        instanceManager.releaseLock(lockKey);
    }

    public long getTotalCallsCount() {
        return instanceManager.getTotalCallsCount();
    }

    public long getOrphanedCallsCount() {
        return instanceManager.getOrphanedCallsCount();
    }

    public long getActiveInstanceCount() {
        return instanceManager.getActiveInstanceCount();
    }

    public Set<String> getActiveInstanceIds() {
        return instanceManager.getActiveInstanceIds();
    }

    /**
     * ========== CALL MANAGEMENT ==========
     */

    public boolean registerCall(Call call) {
        return callRegistry.registerCall(call);
    }

    public boolean updateCall(Call call) {
        return callRegistry.updateCall(call);
    }

    public boolean unregisterCall(String callUuid) {
        return callRegistry.unregisterCall(callUuid);
    }

    public Optional<Call> getCall(String callUuid) {
        return callRegistry.getCall(callUuid);
    }

    public CompletableFuture<Void> updateCallStateWithTimestamp(String callUuid, CallState newState) {
        return callRegistry.updateCallStateWithTimestamp(callUuid, newState);
    }

    public CompletableFuture<Void> addChannelToCall(String callUuid, Channel channel) {
        return callRegistry.addChannelToCall(callUuid, channel);
    }

    public CompletableFuture<Void> addChannelsToCall(String callUuid, Map<String, Channel> channels) {
        return callRegistry.addChannelsToCall(callUuid, channels);
    }

    public List<Call> getActiveCalls() {
        return callRegistry.getActiveCalls();
    }

    public List<Call> getOrphanedCalls() {
        return callRegistry.getOrphanedCalls();
    }

    public Optional<String> getCallForChannel(String channelUuid) {
        return callRegistry.getCallForChannel(channelUuid);
    }

    // DTMF Operations
    public CompletableFuture<Void> appendDTMFEvent(String callUuid, DTMFEvent dtmfEvent) {
        return callRegistry.appendDTMFEvent(callUuid, dtmfEvent);
    }

    public CompletableFuture<Void> clearDTMFHistory(String callUuid) {
        return callRegistry.clearDTMFHistory(callUuid);
    }

    public CompletableFuture<Void> addDTMFSequence(String callUuid, List<DTMFEvent> dtmfEvents) {
        return callRegistry.addDTMFSequence(callUuid, dtmfEvents);
    }

    /**
     * ========== CALL STATE & STATISTICS MANAGEMENT ==========
     */

    public CompletableFuture<Void> updateCallState(String callUuid, CallState newState,
                                                   String campaignId, String instanceId) {
        return metricsService.updateCallState(callUuid, newState, campaignId, instanceId);
    }
}