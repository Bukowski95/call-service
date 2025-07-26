package com.onextel.CallServiceApplication.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.onextel.CallServiceApplication.audit.AuditEventType;
import com.onextel.CallServiceApplication.audit.AuditService;
import com.onextel.CallServiceApplication.common.StringUtils;
import com.onextel.CallServiceApplication.dto.CallStatsResponse;
import com.onextel.CallServiceApplication.exception.CallNotFoundException;
import com.onextel.CallServiceApplication.exception.RequestTimeoutException;
import com.onextel.CallServiceApplication.model.Call;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.Channel;
import com.onextel.CallServiceApplication.model.stats.CampaignStats;
import com.onextel.CallServiceApplication.model.stats.StandaloneStats;
import com.onextel.CallServiceApplication.model.stats.StateTransition;
import com.onextel.CallServiceApplication.service.redis.RedisCallMetricsService;
import com.onextel.CallServiceApplication.service.redis.RedisCallStateManager;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 *
 * Call Manager Cache Characteristics:
 *   - Max calls: ${app.cache.calls.max-size} : default 10000
 *   - Call TTL: ${app.cache.calls.expire-hours} hours : default 1 hour
 *   - Max channels: 2x max calls
 *   - Channel TTL: 1 hour after last access
 *   - Automatic Redis sync on eviction
 *   - Full Redis sync every 15 minutes
 *
 */
@Service
@Slf4j
public class CallManager {
    // CallUuid -> Call
    private final Cache<String, Call> activeCalls;
    // ChannelUuid -> Call
    private final Cache<String, String> channelToCallMap;

    private final RedisCallStateManager redisManager;
    private final RedisCallMetricsService metricsService;
    private final AuditService auditService;

    @Value("${app.cache.full-sync.enabled:true}")  // default true
    private boolean redisFullSyncEnabled;

    @Value("${app.cache.full-sync.interval:3600000}")  // 60 minutes default
    private long redisFullSyncInterval;

    @Value("${app.cache.validation.enabled:true}")  // default true
    private boolean validateCacheEnabled;

    @Value("${app.calls.orphan.recovery.enabled:true}")
    private boolean orphanRecoveryEnabled;

    @Getter
    private String appInstanceId;

    @Autowired
    public CallManager(RedisCallStateManager redisManager,
                       AuditService auditService,
                       RedisCallMetricsService metricsService,
                       @Value("${app.cache.calls.max-size:10000}") int maxCacheCalls,
                       @Value("${app.cache.calls.expire-hours:1}") int callsExpireHours) {
        this.redisManager = Objects.requireNonNull(redisManager, "Redis call manager cannot be null");
        this.auditService = auditService;
        this.metricsService = Objects.requireNonNull(metricsService, "RedisMetricsService cannot be null");

        // Initialize caches
        this.channelToCallMap = Caffeine.newBuilder()
                .maximumSize((maxCacheCalls * 2L)) // Typically more channels than calls
                .expireAfterAccess(callsExpireHours, TimeUnit.HOURS) // Inactive channels expire
                .build();

        this.activeCalls = Caffeine.newBuilder()
                .maximumSize(maxCacheCalls)
                .expireAfterWrite(callsExpireHours, TimeUnit.HOURS)
                .removalListener((String key, Call call, RemovalCause cause) -> {
                    if (call != null && cause.wasEvicted()) {
                        log.warn("Call {} evicted from cache: {}", key, cause);
                        call.getChannels().keySet().forEach(channelUuid -> {
                            channelToCallMap.invalidate(channelUuid);
                            log.debug("Channel {} removed from channelToCallMap", channelUuid);
                        });
                        redisManager.updateCall(call); // Ensure Redis has latest state
                    }
                })
                .recordStats()
                .build();

        log.info("CallManager initialized successfully");
    }

    /**
     * Must be called before using this manager. Initializes appInstanceId for Redis operations.
     */
    public void setAppInstanceId(String appInstanceId) {
        if (appInstanceId != null) {
            this.appInstanceId = appInstanceId;
            redisManager.setAppInstanceId(appInstanceId);
        }
    }

    @PostConstruct
    public void init() {
        try {
            if (orphanRecoveryEnabled) {
                recoverOrphanedCalls();
            }
            Set<String> activeIds = redisManager.getActiveInstanceIds();
            log.info("Currently active instances: {}", activeIds);

        } catch (Exception ex) {
            log.error("Exception while starting CallManager", ex);
        }
    }

    public void shutdown() {
        try {
            log.info("Shutting down CallManager");
            stopScheduledTasks();
            // Transfer ownership of all calls to orphan pool
            redisManager.transferCallsToOrphanPool(appInstanceId);

            // Mark instance as down
            redisManager.markInstanceAsDown(appInstanceId);

            // Unregister instance
            redisManager.unregisterInstance();

            log.info("CallManager shutdown complete");

        } catch (Exception ex) {
            log.error("Failed while shutting down CallManager", ex);
        }

    }

    // ========== ORPHAN CALLS RECOVERY ========== //

    private void recoverOrphanedCalls() {
        metricsService.timeOperation("orphan.recovery.batch", () -> {
            log.info("Started recovering orphaned calls");
            try {
                List<Call> orphans = redisManager.getOrphanedCalls().stream()
                        .filter(call -> !activeCalls.asMap().containsKey(call.getCallUuid()))
                        .peek(call -> log.info("Found orphaned call: {}", call.getCallUuid()))
                        .toList();

                if (!orphans.isEmpty()) {
                    log.info("Recovering {} orphaned calls", orphans.size());

                    metricsService.timeOperation("orphan.recovery.single", () -> {
                        // reconstruct call
                        orphans.forEach(this::recoverCall);
                    });
                }

            } catch (Exception e) {
                log.error("Orphan recovery failed", e);
            }
        });
    }

    private void recoverCall(Call call) {
        String lockKey = "call:recovery:" + call.getCallUuid();
        String oldInstanceId = call.getCallServiceInstanceId();
        try {
            if (!redisManager.acquireLock(lockKey, 30)) {
                log.debug("Skipping recovery - call {} is being recovered by another instance", call.getCallUuid());
                return;
            }

            // Rebuild channel mappings
            call.getChannels().forEach((channelUuid, channel) ->
                    channelToCallMap.put(channelUuid, call.getCallUuid()));

            // Re-register in active calls
            activeCalls.put(call.getCallUuid(), call);

            call.setOrphaned(false);
            call.setCallServiceInstanceId(appInstanceId);
            call.setLastUpdateTimestamp(Instant.now());

            if (!redisManager.updateCall(call)) {
                log.warn("Failed to update Redis state for recovered call {}", call.getCallUuid());
            }

            auditService.logEvent(AuditEventType.CALL_RECOVERED, call.getCallUuid(),
                    null, String.format("Recovered from instance %s", oldInstanceId));

            // TODO: Restart call processing
            // verify call state and if active
            // commandService.restartCallProcessing(call);

        } catch (Exception e) {
            log.error("Failed to recover call {} from old instance {}", call.getCallUuid(), oldInstanceId, e);
            auditService.logEvent(AuditEventType.CALL_RECOVERY_FAILED, call.getCallUuid(), null, e.getMessage());
        } finally {
            redisManager.releaseLock(lockKey);
        }
    }

    // ========== CACHE STATISTICS & MAINTENANCE  ========== //

    @Scheduled(fixedRate =  30 * 60 * 1000) // log every 30 minutes
    public void logCacheStats() {
        try {
            CacheStats statsCallsCache = activeCalls.stats();
            log.info("Call Cache Stats - Hit Rate: {}%, Size: {}, Evictions: {}",
                    statsCallsCache.hitRate() * 100,
                    activeCalls.estimatedSize(),
                    statsCallsCache.evictionCount());

            if (statsCallsCache.evictionCount() > 1000) {
                log.warn("High calls cache eviction rate: {}", statsCallsCache.evictionCount());
            }
            if (statsCallsCache.hitRate() < 0.7) {
                log.warn("Low calls cache hit rate: {}%", statsCallsCache.hitRate() * 100);
            }

            CacheStats statsChannelsCache = channelToCallMap.stats();
            log.info("Channels Cache Stats - Hit Rate: {}%, Size: {}, Evictions: {}",
                    statsChannelsCache.hitRate() * 100,
                    channelToCallMap.estimatedSize(),
                    statsChannelsCache.evictionCount());

            if (statsChannelsCache.evictionCount() > 1000) {
                log.warn("High channels cache eviction rate: {}", statsChannelsCache.evictionCount());
            }
            if (statsChannelsCache.hitRate() < 0.7) {
                log.warn("Low channels cache hit rate: {}%", statsChannelsCache.hitRate() * 100);
            }
        } catch (Exception exp) {
            log.error("Failed to get Cache stats", exp);
        }
    }

    @Scheduled(fixedRateString = "${app.redis.validation.interval:1800000}") // 30 minutes
    public void validateCacheConsistency() {
        if (!validateCacheEnabled) return; // skip if not enabled
        if (activeCalls.asMap().isEmpty()) return; // Skip if no active calls
        try {
            activeCalls.asMap().forEach((callUuid, call) -> {
                Optional<Call> redisCall = redisManager.getCall(callUuid);
                if (redisCall.isEmpty()) {
                    log.warn("Call {} in cache but missing in Redis - re-registering", callUuid);
                    redisManager.registerCall(call);
                }
            });
        } catch (Exception exp) {
            log.error("Cache --> Redis sync failed", exp);
        }
    }

    @Scheduled(fixedRateString = "${app.cache.full-sync.interval:3600000}") // 60 minutes
    public void fullCacheSync() {
        if (!redisFullSyncEnabled) return; // skip if not enabled
        if (activeCalls.asMap().isEmpty()) return; // Skip if no active calls

        try {
            activeCalls.asMap().forEach((callUuid, call) -> {
                if (!redisManager.updateCall(call)) {
                    log.error("Failed to sync call {} to Redis", callUuid);
                }
            });
        } catch (Exception exp) {
            log.error("Cache --> Redis full sync failed", exp);
        }
    }

    public void stopScheduledTasks() {
        stopCacheValidation(); // Disable the cache validation task
        stopRedisFullSync(); // Disable the full sync task
        log.info("Scheduled tasks stopped.");
    }

    public void stopCacheValidation() {
        validateCacheEnabled = false;
        log.info("Cache validation task disabled.");
    }

    public void stopRedisFullSync() {
        redisFullSyncEnabled = false;
        log.info("Full cache sync task disabled.");
    }

    public void restartCacheValidation() {
        validateCacheEnabled = true;
        log.info("Cache validation task restarted.");
    }

    public void restartRedisFullSync() {
        redisFullSyncEnabled = true;
        log.info("Full cache->redis sync task restarted.");
    }

    // ========== CALL LIFECYCLE MANAGEMENT ========== //

    public void registerCall(Call call) {
        call.setCallServiceInstanceId(appInstanceId);
        call.setLastUpdateTimestamp(Instant.now());

        activeCalls.put(call.getCallUuid(), call);
        call.getChannels().keySet().forEach(channelUuid ->
                channelToCallMap.put(channelUuid, call.getCallUuid()));

        if (!redisManager.registerCall(call)) {
            log.error("Failed to register call in Redis: {}", call.getCallUuid());
        }

        auditService.logEvent(AuditEventType.CALL_REGISTERED,
                call.getCallUuid(),
                null,
                "New call registered"
        );
        log.info("Registered call {}", call.getCallUuid());
    }

    public void unregisterCall(String callUuid) {
        Call call = activeCalls.getIfPresent(callUuid);
        if (call != null) {
             // Do not remove from local cache - aut delete after 1 hour
            // call.getChannels().keySet().forEach(channelToCallMap::invalidate);
            // activeCalls.invalidate(callUuid);

            if (!redisManager.unregisterCall(callUuid)) {
                log.error("Failed to unregister call from Redis: {}", callUuid);
            }

            log.info("Unregistered call {}", callUuid);
            auditService.logEvent(AuditEventType.CALL_UNREGISTERED,
                    callUuid,
                    null,
                    "Call unregistered after " + call.getDuration().toSeconds() + " seconds"
            );
        }
    }

    public void finalizeCall(String callUuid) {
        getCall(callUuid).ifPresent(call -> {
            // Release resources
            // Generate CDR
            unregisterCall(callUuid);
        });
    }

    // ========== Call State Management ==========

    public void updateCallState(String callUuid, CallState newState) {
        Call call = activeCalls.getIfPresent(callUuid);
        if (call == null) {
            log.warn("Attempted to update state for non-existent call: {}", callUuid);
            return;
        }

        CallState previousState = call.getCurrentState();
        call.updateCallState(newState);
        if (!redisManager.updateCall(call)) {
            log.error("Failed to update call state in Redis: {}", callUuid);
        }

        auditService.logEvent(AuditEventType.CALL_STATE_CHANGED,
                callUuid,
                null,
                String.format("State changed from %s to %s", previousState, newState)
        );

        // Campaign call
        //  redisManager.updateCallState(callUuid, CallState.RINGING, "campaign-123", "instance-xyz");

        // Standalone call
        redisManager.updateCallState(callUuid, newState, null, null);
    }

    public void addChannelToCall(String callUuid, Channel channel) {
        Call call = activeCalls.getIfPresent(callUuid);
        if (call != null) {
            call.addChannel(channel);
            channelToCallMap.put(channel.getChannelUuid(), callUuid);
            if (!redisManager.updateCall(call)) {
                log.error("Failed to update call with new channel");
            }

            log.info("Added channel {} to call {}", channel.getChannelUuid(), callUuid);
            auditService.logEvent(AuditEventType.CHANNEL_ADDED,
                    callUuid,
                    channel.getChannelUuid(),
                    "Channel added"
            );
        }
    }

    public void removeChannel(String channelUuid) {
        String callUuid = channelToCallMap.getIfPresent(channelUuid);
        if (callUuid != null) {
            Call call = activeCalls.getIfPresent(callUuid);
            if (call != null) {
                call.getChannel(channelUuid).ifPresent(channel -> {
                    channel.hangup("REMOVED_BY_SYSTEM");
                    if (!redisManager.updateCall(call)) {
                        log.error("Failed to update call after channel removal");
                    }
                });

                log.info("Removed channel {} from call {}", channelUuid, callUuid);
                auditService.logEvent(AuditEventType.CHANNEL_REMOVED,
                        callUuid,
                        channelUuid,
                        "Channel removed"
                );
            }
        }
    }

    public void cleanupOrphanedChannel(String channelUuid) {
        // Remove from channelToCallMap if exists
        String callUuid = channelToCallMap.getIfPresent(channelUuid);
        if (callUuid != null) {
            channelToCallMap.invalidate(channelUuid);
            // clean up the channel from the Call object
            Call call = activeCalls.getIfPresent(callUuid);
            if (call != null) {
                call.removeChannel(channelUuid);

                // If call has no more channels, consider cleaning it up
                if (call.getChannels().isEmpty()) {
                    activeCalls.invalidate(callUuid);
                }
            }
        }
    }

    public Optional<Call> getCall(String callUuid) {
        try {
            return Optional.ofNullable(activeCalls.getIfPresent(callUuid));
        } catch (Exception e) {
            log.warn("Cache access failed for call {}, falling back to Redis", callUuid, e);
            return redisManager.getCall(callUuid);
        }
    }

    public Optional<Call> getCallByChannel(String channelUuid) {
        String callUuid = channelToCallMap.getIfPresent(channelUuid);
        if (!StringUtils.isNullOrBlank(callUuid)) {
            return getCall(callUuid);
        }
        return redisManager.getCallForChannel(channelUuid).flatMap(this::getCall);
    }

    public boolean isCallActive(String callUuid) {
        return getCall(callUuid)
                .map(call -> call.getCurrentState().isActive())
                .orElse(false);
    }

    public List<Call> getCallsByState(CallState state) {
        return activeCalls.asMap().values().stream()
                .filter(call -> call.getCurrentState() == state)
                .toList();
    }

    public List<Call> getLastUpdatedCallsByState(CallState state) {
        return activeCalls.asMap().values().stream()
                .filter(call -> call.getCurrentState() == state)
                .sorted(Comparator.comparing(Call::getLastUpdateTimestamp))
                .collect(Collectors.toList());
    }

    public List<Call> getActiveCalls() {
        return new ArrayList<>(activeCalls.asMap().values());
    }

    /**
     * ========== Call Stats  ==========
     */
    public CompletableFuture<CallStatsResponse> getCallStats(String callUuid) {
        Optional<Call> optionalCall = getCall(callUuid);
        if (optionalCall.isEmpty()) {
            String message = String.format("Call with %s not found", callUuid);
            log.warn(message);
            throw new CallNotFoundException(message);
        }
        CompletableFuture<CallState> currentStateFuture = metricsService.getCurrentState(callUuid);
        CompletableFuture<List<StateTransition>> historyFuture = metricsService.getStateHistory(callUuid);

        return CompletableFuture.allOf(currentStateFuture, historyFuture)
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(v -> {
                    CallState currentState = currentStateFuture.join();
                    List<StateTransition> history = historyFuture.join();
                    return new CallStatsResponse(callUuid, currentState, history);
                }).exceptionally(ex -> {
                    if (ex.getCause() instanceof TimeoutException) {
                        throw new CompletionException(
                                new RequestTimeoutException("Metrics service timeout for call: " + callUuid)
                        );
                    }
                    throw new CompletionException(ex);
                });
    }

    public CompletableFuture<CampaignStats> getCampaignStats(String campaignId) {
        return metricsService.getCampaignStats(campaignId);
    }

    public CompletableFuture<CampaignStats> getCampaignInstanceStats(String campaignId, String instanceId) {
        return metricsService.getCampaignInstanceStats(campaignId, instanceId);
    }

    public CompletableFuture<StandaloneStats> getStandaloneStats() {
        return metricsService.getStandaloneStats();
    }

    public Map<CallState, Long> getCallStateStatistics() {
        return activeCalls.asMap().values().stream()
                .collect(Collectors.groupingBy(
                        Call::getCurrentState,
                        Collectors.counting()
                ));
    }

    public List<Call> getLongRunningCalls(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        return activeCalls.asMap().values().stream()
                .filter(c -> c.getAnswerTime() != null)
                .filter(c -> c.getAnswerTime().isBefore(cutoff))
                .collect(Collectors.toList());
    }

    private void dumpStats() {
        try {
            // Get campaign overview
            String campaignId = "campaign-123";
            CampaignStats campaign = metricsService.getCampaignStats(campaignId).get();
            log.info("Campaign {} total call stats:  counts {} completed {} failed {}",
                    campaignId,
                    campaign.getStateCounts(),
                    campaign.getCompleted(),
                    campaign.getFailed());

            // Get instance details
            String campaignInstanceId = "instance-xyz";
            CampaignStats campaignInstance = metricsService.getCampaignInstanceStats(campaignId, campaignInstanceId).get();
            log.info("Campaign {} instance {} call stats: counts {} completed {} failed {}",
                    campaignId,
                    campaignInstanceId,
                    campaignInstance.getStateCounts(),
                    campaignInstance.getCompleted(),
                    campaignInstance.getFailed());

            // Get standalone calls
            StandaloneStats standalone = metricsService.getStandaloneStats().get();
            log.info("Standalone call stats: counts {} completed {} failed {}",
                    standalone.getStateCounts(),
                    standalone.getCompleted(),
                    standalone.getFailed());
        } catch (Exception e) {
            log.info("Exception while getting stats", e);
        }
    }
}