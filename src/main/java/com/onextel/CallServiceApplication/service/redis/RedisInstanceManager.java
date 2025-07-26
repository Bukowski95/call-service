package com.onextel.CallServiceApplication.service.redis;

import com.onextel.CallServiceApplication.common.ThreadUtils;
import com.onextel.CallServiceApplication.util.HostNameProvider;
import com.onextel.CallServiceApplication.util.RedisCommandUtils;
import io.lettuce.core.*;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisInstanceManager {
    private final RedisConnectionPool connectionPool;
    private final RedisCallMetricsService metricsService;
    private String instanceId;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicInteger orphanCleanupInProgress = new AtomicInteger(0);

    public synchronized void setAppInstanceId(String appInstanceId) {
        if (isInitialized.compareAndSet(false, true)) {
            registerInstance(appInstanceId, HostNameProvider.getHostname(), 8080, "1");
        } else {
            log.warn("App instance ID already set for RedisInstanceManager");
        }
    }

    public void registerInstance(String instanceId, String host, int port, String version) {
        log.info("registerInstance {}", instanceId);
        this.instanceId = instanceId;
        connectionPool.executeWithConnection("registerInstance", conn -> {
            RedisCommands<String, String> commands = conn.sync();

            commands.multi();
            // Set instance metadata with TTL
            commands.hmset(RedisKeys.instanceMetadataKey(instanceId), Map.of(
                    RedisKeys.InstanceMetadata.ID, instanceId,
                    RedisKeys.InstanceMetadata.HOST, host,
                    RedisKeys.InstanceMetadata.PORT, String.valueOf(port),
                    RedisKeys.InstanceMetadata.VERSION, version,
                    RedisKeys.InstanceMetadata.LAST_SEEN, Instant.now().toString(),
                    RedisKeys.InstanceMetadata.STATUS, RedisKeys.InstanceMetadata.STATUS_ACTIVE
            ));
            // hold instance metadata for 24 hours
            commands.expire(RedisKeys.instanceMetadataKey(instanceId), RedisKeys.TTL.METADATA_RETENTION_SECONDS);

            // Register instance
            commands.sadd(RedisKeys.INSTANCE_REGISTRY_KEY, instanceId);
            commands.zadd(RedisKeys.HEARTBEAT_KEY, System.currentTimeMillis()/1000.0, instanceId);
            commands.exec();

            log.info("Registered instance {}", instanceId);
            return null;
        });

    }

    public void unregisterInstance() {
        log.info("unregisterInstance {}", instanceId);
        if (instanceId == null) return;

        connectionPool.executeWithConnection( "unregisterInstance", conn -> {
            RedisCommands<String, String> commands = conn.sync();
            commands.multi();
            // remove from instance registry
            commands.srem(RedisKeys.INSTANCE_REGISTRY_KEY, instanceId);
            //remove heartbeat
            commands.zrem(RedisKeys.HEARTBEAT_KEY, instanceId);
            //remove calls id for this instance (calls are already marked as orphan calls for this instance)
            commands.del(RedisKeys.instanceCallsKey(instanceId));
            // do no delete instance metadata - will auto cleanup after 24 hours
            //commands.del(RedisKeys.instanceMetadataKey(instanceId));
            commands.exec();
            return null;
        });
    }

    public void transferCallsToOrphanPool(String instanceId) {
        log.info("transferCallsToOrphanPool instance {}", instanceId);
        connectionPool.executeWithConnection("transferCallsToOrphanPool", conn -> {
            RedisCommands<String, String> commands = conn.sync();

            // Get all call IDs
            Set<String> callIds = commands.smembers(RedisKeys.instanceCallsKey(instanceId));
            log.info("transferCallsToOrphanPool instance {} no calls available", instanceId);
            if (callIds.isEmpty()) {
                return null;
            }

            // Pipeline the orphan transfer
            commands.multi();
            callIds.forEach(callId -> {
                commands.zadd(RedisKeys.ORPHANED_CALLS_ZSET,
                        System.currentTimeMillis(), callId);
                commands.hset(RedisKeys.callKey(callId), Map.of(
                        RedisKeys.CallFields.IS_ORPHAN, "true",
                        RedisKeys.CallFields.ORIGINAL_INSTANCE , instanceId,
                        RedisKeys.CallFields.ORPHANED_AT, Instant.now().toString()));
            });
            commands.del(RedisKeys.instanceCallsKey(instanceId));
            commands.exec();

            log.info("Transferred {} calls to orphan pool", callIds.size());
            return null;
        });
    }

    public void markInstanceAsDown(String appInstanceId) {
        log.info("MarkInstanceAsDown {}", appInstanceId);
        connectionPool.executeWithConnection( "MarkInstanceAsDown", conn -> {
            RedisCommands<String, String> commands = conn.sync();
            // Mark instance as down and clean up
            // Mark as down and set expiration (24 hours for audit)
            commands.hset(
                    RedisKeys.instanceMetadataKey(appInstanceId),
                    Map.of(
                            RedisKeys.InstanceMetadata.STATUS, RedisKeys.InstanceMetadata.STATUS_DOWN,
                            RedisKeys.InstanceMetadata.REMOVED_AT, Instant.now().toString()
                    ));
            commands.srem(RedisKeys.INSTANCE_REGISTRY_KEY, appInstanceId);
            commands.zrem(RedisKeys.HEARTBEAT_KEY, appInstanceId);
            return null;
        });
    }

    @Scheduled(fixedRate = RedisKeys.Intervals.HEARTBEAT_MS)
    private void sendHeartbeat() {
        if (instanceId == null) {
            return;
        }
        connectionPool.executeWithConnection("sendHeartbeat", conn -> {
            RedisCommands<String, String> commands = conn.sync();
            double currentTime = System.currentTimeMillis()/1000.0;
            commands.multi();
            commands.zadd(RedisKeys.HEARTBEAT_KEY, currentTime, instanceId);
            commands.expire(RedisKeys.HEARTBEAT_KEY, RedisKeys.TTL.INSTANCE_SECONDS * 2);
            commands.hset(RedisKeys.instanceMetadataKey(instanceId),
                    RedisKeys.InstanceMetadata.LAST_SEEN, Instant.now().toString());
            // update TTL for metadata
            commands.expire(RedisKeys.instanceMetadataKey(instanceId), RedisKeys.TTL.METADATA_RETENTION_SECONDS);
            commands.exec();
            return null;
        });
    }

    /**
     * ========== ORPHAN DETECTION & RECOVERY ==========
     * Scheduled orphan calls check every 15 minutes
     * Dead instance detection (if missed 2 heartbeats * 2)
     * Automatic marking of orphaned calls with 24h TTL
     * Periodic cleanup of expired orphans
     */

    @Scheduled(fixedRate = RedisKeys.Intervals.ORPHAN_CHECK_MS, initialDelay = RedisKeys.Intervals.ORPHAN_CHECK_MS) // 15 minutes
    private void detectAndHandleOrphanedCalls() {
        log.info("detectAndHandleOrphanedCalls for {} ", instanceId);
        if (orphanCleanupInProgress.get() > 0) {
            log.debug("Orphan cleanup already in progress");
            return;
        }
        orphanCleanupInProgress.incrementAndGet();
        try {
            // Detect and clean dead instances
            List<String> deadInstances = detectDownInstances();
            if (!deadInstances.isEmpty()) {
                log.warn("Detected {} dead instances: {}", deadInstances.size(), deadInstances);
                // Atomic cleanup operation
                markCallsAsOrphaned(deadInstances);
                cleanupDeadInstances(deadInstances);
            }
            // Regular orphaned call cleanup
            cleanupOrphanedCalls();
        } catch (Exception ex) {
            log.error("Failed to clean dead instances", ex);
        } finally {
            orphanCleanupInProgress.decrementAndGet();
        }
    }

    private List<String> detectDownInstances() {
        log.info("detectDownInstances for {} ", instanceId);
        return connectionPool.executeWithConnection( "detectDownInstances",conn -> {
            long cutoff = (System.currentTimeMillis()/1000) - (RedisKeys.TTL.INSTANCE_SECONDS * 2);
            Set<String> activeInstances = new HashSet<>(conn.sync()
                    .zrangebyscore(RedisKeys.HEARTBEAT_KEY, Range.create(cutoff, Double.POSITIVE_INFINITY)));

            Set<String> allInstances = conn.sync().smembers(RedisKeys.INSTANCE_REGISTRY_KEY);
            allInstances.removeAll(activeInstances);

            return new ArrayList<>(allInstances);
        });
    }

    private void markCallsAsOrphaned(List<String> instanceIds) {
        log.info("markCallsAsOrphaned {} ", instanceId);
        connectionPool.executeWithConnection("markCallsAsOrphaned", conn -> {
            RedisCommands<String, String> commands = conn.sync();

            instanceIds.forEach(instanceId -> {
                Set<String> callIds = commands.smembers(RedisKeys.instanceCallsKey(instanceId));
                if (callIds.isEmpty()) return;

                commands.multi();
                callIds.forEach(callId -> {
                    commands.zadd(RedisKeys.ORPHANED_CALLS_ZSET,
                            System.currentTimeMillis(), callId);
                    commands.hset(RedisKeys.callKey(callId), Map.of(
                            RedisKeys.CallFields.IS_ORPHAN, "true",
                            RedisKeys.CallFields.ORIGINAL_INSTANCE, instanceId,
                            RedisKeys.CallFields.ORPHANED_AT, Instant.now().toString()));
                });
                commands.del(RedisKeys.instanceCallsKey(instanceId));
                commands.exec();

                log.info("Marked {} calls as orphaned from instance {}", callIds.size(), instanceId);
            });
            return null;
        });
    }

    private void cleanupDeadInstances(List<String> instanceIds) {
        connectionPool.executeWithConnection("cleanupDeadInstances", conn -> {
            RedisCommands<String, String> commands = conn.sync();

            commands.multi();
            instanceIds.forEach(instanceId -> {
                commands.zrem(RedisKeys.HEARTBEAT_KEY, instanceId);
                commands.srem(RedisKeys.INSTANCE_REGISTRY_KEY, instanceId);
                commands.hset(RedisKeys.instanceMetadataKey(instanceId),
                        RedisKeys.InstanceMetadata.STATUS, RedisKeys.InstanceMetadata.STATUS_DOWN);
                // Only set TTL if it doesn't exist yet
                Long ttl = commands.ttl(RedisKeys.instanceMetadataKey(instanceId));
                if (ttl == null || ttl == -1) {
                    commands.expire(
                            RedisKeys.instanceMetadataKey(instanceId),
                            RedisKeys.TTL.METADATA_RETENTION_SECONDS
                    );
                }
            });

            // Remove calls - TODO: Check if need to remove calls
            commands.del(RedisKeys.instanceCallsKey(instanceId));
            commands.exec();
            return null;
        });
    }



    public boolean acquireLock(String lockKey, int ttlSeconds) {
        return connectionPool.executeWithConnection("acquireLock", conn -> {
            RedisCommands<String, String> commands = conn.sync();
            return "OK".equals(commands.set(lockKey, instanceId,
                    SetArgs.Builder.nx().ex(ttlSeconds)));
        });
    }

    public void releaseLock(String lockKey) {
        connectionPool.executeWithConnection("releaseLock", conn -> {
            String luaScript =
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                            "   return redis.call('DEL', KEYS[1]) " +
                            "else " +
                            "   return 0 " +
                            "end";
            conn.sync().eval(luaScript, ScriptOutputType.INTEGER,
                    new String[]{lockKey}, instanceId);
            return null;
        });
    }

    public long getOrphanedCallsCount() {
        return connectionPool.executeWithConnection("getOrphanCount", conn -> {
            return conn.sync().zcard(RedisKeys.ORPHANED_CALLS_ZSET);
        });
    }

    public long getTotalCallsCount() {
        return connectionPool.executeWithConnection("getTotalCalls", conn -> {
            RedisCommands<String, String> sync = conn.sync();
            ScanCursor cursor = ScanCursor.INITIAL;
            long total = 0;
            do {
                KeyScanCursor<String> result = sync.scan(cursor, ScanArgs.Builder.matches(RedisKeys.CALL_KEY_PREFIX + "*").limit(100));
                total += result.getKeys().size();
                cursor = result;
            } while (!cursor.isFinished());
            return total;
        });
    }

//    public long getTotalCallsCount() {
//        return connectionPool.executeWithConnection("getTotalCalls", conn -> {
//            return conn.sync().keys(RedisKeys.CALL_KEY_PREFIX + "*").size();
//        });
//    }

    /**
     * Gets current count of active instances with recent heartbeats
     * @return Number of instances considered active
     */
    public long getActiveInstanceCount() {
        return connectionPool.executeWithConnection("getActiveInstanceCount", conn -> {
            RedisCommands<String, String> commands = conn.sync();

            // Calculate cutoff timestamp (current time - instance TTL)
            long cutoff = (System.currentTimeMillis() / 1000) - RedisKeys.TTL.INSTANCE_SECONDS;

            try {
                // Count instances with recent heartbeats
                Long count = commands.zcount(
                        RedisKeys.HEARTBEAT_KEY,
                        Range.create(cutoff, Double.POSITIVE_INFINITY)
                );

                return count != null ? count : 0L;
            } catch (Exception e) {
                log.error("Failed to get active instance count", e);
                return 0L; // Fail-safe default
            }
        });
    }

    /**
     * Gets detailed active instance IDs
     * @return Set of active instance IDs
     */
    public Set<String> getActiveInstanceIds() {
        return connectionPool.executeWithConnection("getActiveInstanceIds", conn -> {
            RedisCommands<String, String> commands = conn.sync();
            long cutoff = (System.currentTimeMillis() / 1000) - RedisKeys.TTL.INSTANCE_SECONDS;

            return new HashSet<>(commands.zrangebyscore(
                    RedisKeys.HEARTBEAT_KEY,
                    Range.create(cutoff, Double.POSITIVE_INFINITY)
            ));
        });
    }

    private void cleanupOrphanedCalls() {
        connectionPool.executeWithConnection("cleanupOrphanedCalls", conn -> {
            RedisAsyncCommands<String, String> async = conn.async();

            // Get orphaned calls older than retention period
            long cutoff = System.currentTimeMillis() -
                    (RedisKeys.TTL.ORPHANED_CALL_SECONDS * 1000);

            // Process in batches
            int batchSize = 100;
            int deleted = 0;
            String cursor = "0";

            do {
                // Scan orphaned calls zset
                RedisFuture<ScoredValueScanCursor<String>> scanFuture =
                        async.zscan(RedisKeys.ORPHANED_CALLS_ZSET,
                                ScanArgs.Builder.limit(batchSize));

                ScoredValueScanCursor<String> result = scanFuture.get();
                List<ScoredValue<String>> oldOrphans = result.getValues().stream()
                        .filter(sv -> sv.getScore() < cutoff)
                        .toList();

                if (!oldOrphans.isEmpty()) {
                    // Delete in pipeline
                    RedisFuture<Long> delFuture = async.zrem(
                            RedisKeys.ORPHANED_CALLS_ZSET,
                            oldOrphans.stream().map(ScoredValue::getValue).toArray(String[]::new));

                    // Delete call objects
                    oldOrphans.forEach(sv -> {
                        async.del(RedisKeys.callKey(sv.getValue()));
                    });

                    deleted += oldOrphans.size();
                    delFuture.get(); // Wait for completion
                }

                cursor = result.getCursor();
            } while (!cursor.equals("0") && deleted < 1000); // Limit per iteration

            if (deleted > 0) {
                log.info("Cleaned up {} expired orphaned calls", deleted);
            }
            return null;
        });
    }

    private void cleanupOrphanedCallsOld() {
        connectionPool.executeWithConnection("cleanupOrphanedCalls", conn -> {
            // Set connection timeout
            conn.setTimeout(Duration.ofSeconds(30));

            // Get async interface
            RedisAsyncCommands<String, String> asyncCommands = conn.async();
            final int BATCH_SIZE = 100;
            final int MAX_ITERATIONS = 500;
            final int PAUSE_EVERY = 500;
            final long CUTOFF = System.currentTimeMillis() - (RedisKeys.TTL.ORPHANED_CALL_SECONDS * 1000);

            // Distributed lock setup
            String lockKey = "orphan-cleanup-lock";
            String lockId = UUID.randomUUID().toString();

            try {
                // Async lock acquisition
                RedisFuture<String> lockFuture = asyncCommands.set(lockKey, lockId,
                        SetArgs.Builder.nx().ex(30));

                if (!"OK".equals(lockFuture.get())) {
                    log.warn("Cleanup already in progress by another instance");
                    return null;
                }

                AtomicInteger totalProcessed = new AtomicInteger();
                AtomicInteger totalCleaned = new AtomicInteger();
                int iterations = 0;
                String lastCursor = null;

                ScanCursor cursor = ScanCursor.INITIAL;
                do {
                    iterations++;

                    // Safety checks
                    if (iterations > MAX_ITERATIONS) {
                        log.warn("Reached maximum iterations ({})", MAX_ITERATIONS);
                        break;
                    }

                    if (cursor.getCursor().equals(lastCursor)) {
                        log.warn("Cursor stuck at {}", cursor.getCursor());
                        break;
                    }
                    lastCursor = cursor.getCursor();

                    // Process batch
                    RedisFuture<KeyScanCursor<String>> scanFuture = asyncCommands.scan(
                            cursor,
                            ScanArgs.Builder.matches(RedisKeys.CALL_KEY_PREFIX + "*").limit(BATCH_SIZE)
                    );

                    KeyScanCursor<String> scanResult = scanFuture.get();
                    List<String> keys = scanResult.getKeys();

                    if (keys != null && !keys.isEmpty()) {
                        List<RedisFuture<?>> operationFutures = new ArrayList<>();

                        for (String callKey : keys) {
                            try {
                                // Skip non-call keys
                                if (callKey.startsWith(RedisKeys.CALL_STATE_PREFIX) ||
                                        callKey.startsWith(RedisKeys.STATE_HISTORY_PREFIX)) {
                                    continue;
                                }

                                // Pipeline the operations
                                RedisFuture<String> isOrphanFuture = RedisCommandUtils.jsonGetAsync(asyncCommands, callKey, "$.isOrphan");
                                RedisFuture<String> lastUpdatedFuture = RedisCommandUtils.jsonGetAsync(asyncCommands, callKey, "$.lastUpdateTimestamp");

                                // Store futures to check results later
                                operationFutures.add(isOrphanFuture);
                                operationFutures.add(lastUpdatedFuture);

                                // Chain the deletion if conditions are met
                                CompletableFuture.allOf(
                                        isOrphanFuture.toCompletableFuture(),
                                        lastUpdatedFuture.toCompletableFuture()
                                ).thenAccept(__ -> {
                                    try {
                                        String isOrphan = isOrphanFuture.get();
                                        String lastUpdated = lastUpdatedFuture.get();

                                        if ("true".equals(isOrphan) && lastUpdated != null &&
                                                Instant.parse(lastUpdated).isBefore(Instant.ofEpochMilli(CUTOFF))) {
                                            asyncCommands.del(callKey).thenAccept(delCount -> {
                                                if (delCount > 0) {
                                                    totalCleaned.getAndIncrement();
                                                }
                                            });
                                        }
                                        totalProcessed.getAndIncrement();
                                    } catch (Exception e) {
                                        log.warn("Error processing call {}", callKey, e);
                                    }
                                });

                                // Refresh lock periodically
                                if (totalProcessed.get() % 100 == 0) {
                                    asyncCommands.expire(lockKey, 30);
                                }

                            } catch (Exception e) {
                                log.warn("Error queueing operations for {}", callKey, e);
                            }
                        }

                        // Wait for current batch to complete
                        LettuceFutures.awaitAll(10, TimeUnit.SECONDS,
                                operationFutures.toArray(new RedisFuture[0]));

                        // Small pause between batches
                        if (iterations % 10 == 0) {
                            ThreadUtils.safeSleep(25);
                        }
                    }

                    cursor = ScanCursor.of(scanResult.getCursor());

                    if (cursor.getCursor().equals("0") && keys != null && keys.isEmpty()) {
                        break;
                    }
                } while (!cursor.isFinished());

                log.info("Cleaned {} orphaned calls from {} processed", totalCleaned, totalProcessed);

            } catch (Exception e) {
                log.error("Orphan cleanup failed", e);
            } finally {
                // Atomic lock release with Lua script
                String luaScript =
                        "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                                "   return redis.call('DEL', KEYS[1]) " +
                                "else " +
                                "   return 0 " +
                                "end";

                asyncCommands.eval(luaScript, ScriptOutputType.INTEGER,
                        new String[]{lockKey}, lockId);
            }
            return null;
        });
    }

    //    private void markCallsAsOrphanedOld(List<String> instanceIds) {
//        connectionPool.executeWithConnection("markCallsAsOrphaned", connection -> {
//            RedisCommands<String, String> syncCommands = connection.sync();
//
//            for (String instanceId : instanceIds) {
//                Set<String> callUuids = getAndClearInstanceCalls(syncCommands, instanceId);
//                markCallsOrphaned(connection, callUuids); // Pass the connection here
//                updateInstanceMetadata(syncCommands, instanceId);
//            }
//            return null;
//        });
//    }
//
//    private Set<String> getAndClearInstanceCalls(RedisCommands<String, String> commands, String instanceCallsKey) {
//        // Start transaction
//        commands.multi();
//
//        // Queue commands
//        commands.smembers(instanceCallsKey);
//        commands.del(instanceCallsKey);
//
//        // Execute transaction and get results
//        TransactionResult results = commands.exec();
//
//        // Process results - first command is SMEMBERS result
//        if (results == null || results.size() < 2) {
//            log.warn("Transaction failed or returned unexpected results");
//            return Collections.emptySet();
//        }
//
//        // Safely cast the SMEMBERS result (first command in transaction)
//        @SuppressWarnings("unchecked")
//        Set<String> callUuids = (Set<String>) results.get(0);
//        return callUuids != null ? callUuids : Collections.emptySet();
//    }
//
//    private void markCallsOrphaned(StatefulRedisConnection<String, String> connection, Set<String> callUuids) {
//        if (callUuids == null || callUuids.isEmpty()) {
//            return;
//        }
//
//        RedisAsyncCommands<String, String> asyncCommands = connection.async();
//        List<RedisFuture<?>> futures = new ArrayList<>(callUuids.size() * 2); // Pre-size for 2 ops per call
//
//        try {
//            // Batch all operations first
//            for (String callUuid : callUuids) {
//                try {
//                    futures.add(RedisCommandUtils.jsonSetAsync(
//                            asyncCommands,
//                            RedisKeys.callKey(callUuid),
//                            "$.isOrphan",
//                            "true"
//                    ));
//                    futures.add(asyncCommands.expire(
//                            RedisKeys.callKey(callUuid),
//                            RedisKeys.TTL.ORPHANED_CALL_SECONDS
//                    ));
//                } catch (Exception e) {
//                    log.error("Failed to queue orphan marking for call {}", callUuid, e);
//                    // Continue with remaining calls
//                }
//            }
//
//            // Execute batch with timeout
//            LettuceFutures.awaitAll(
//                    RedisKeys.Intervals.ORPHAN_MARKING_TIMEOUT_SECONDS,
//                    TimeUnit.SECONDS,
//                    futures.toArray(new RedisFuture[0])
//            );
//
//            // Verify results
//            for (RedisFuture<?> future : futures) {
//                if (!future.isDone() || future.isCancelled()) {
//                    log.warn("Orphan marking operation failed to complete");
//                }
//            }
//        } catch (Exception e) {
//            log.error("Batch orphan marking failed", e);
//            throw new RuntimeException("Failed to mark calls as orphaned", e);
//        }
//    }

//    private void updateInstanceMetadata(RedisCommands<String, String> commands, String instanceId) {
//        commands.multi();
//        commands.hset(
//                RedisKeys.instanceMetadataKey(instanceId),
//                Map.of(
//                        RedisKeys.InstanceMetadata.STATUS, RedisKeys.InstanceMetadata.STATUS_DOWN,
//                        RedisKeys.InstanceMetadata.REMOVED_AT, Instant.now().toString()
//                ));
//        commands.srem(RedisKeys.INSTANCE_REGISTRY_KEY, instanceId);
//        commands.zrem(RedisKeys.HEARTBEAT_KEY, instanceId);
//        commands.exec();
//    }
}
