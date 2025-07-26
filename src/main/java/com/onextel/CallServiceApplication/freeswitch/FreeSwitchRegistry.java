package com.onextel.CallServiceApplication.freeswitch;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.onextel.CallServiceApplication.freeswitch.event.Event;
import com.onextel.CallServiceApplication.freeswitch.event.EventParams;
import com.onextel.CallServiceApplication.util.RedisPubSubManager;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Getter
@Setter
public class FreeSwitchRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeSwitchRegistry.class);
    private static final String ALL_NODES_KEY = "freeswitch:nodes:hash:";
    private static final String HEALTHY_NODES_KEY = "freeswitch:nodes:healthy";
    private static final String NODE_UPDATES_CHANNEL = "freeswitch:node_updates";
    private static final String LOCK_PREFIX = "lock:";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(3);

    private final Cache<String, FreeSwitchNode> localCache;
    private final RedisTemplate<String, String> redisLockTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ScheduledExecutorService scheduler;
    private final RedisPubSubManager pubSubManager;

    @Configuration
    public static class CacheConfig {
        @Bean
        public Cache<String, FreeSwitchNode> redisFreeSwitchNodeCache() {
            return Caffeine.newBuilder()
                    .maximumSize(50)
                    .expireAfterWrite(30, TimeUnit.SECONDS)
                    .removalListener((key, value, cause) ->
                            LoggerFactory.getLogger(FreeSwitchRegistry.class)
                                    .trace("Evicted node {}: {}", key, cause))
                    .build();
        }
    }

    @Autowired
    public FreeSwitchRegistry(
            Cache<String, FreeSwitchNode> redisFreeSwitchNodeCache,
            @Qualifier("blockingRedisLockTemplate") RedisTemplate<String, String> redisLockTemplate,
            @Qualifier("redisObjectTemplate") RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisPubSubManager pubSubManager) {

        this.localCache = redisFreeSwitchNodeCache;
        this.redisLockTemplate = redisLockTemplate;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "fs-node-cleaner")
        );
        this.pubSubManager = pubSubManager;
        startBackgroundTasks();
    }

    private void startBackgroundTasks() {
        // Start node cleanup task
        this.scheduler.scheduleAtFixedRate(this::cleanupInactiveNodes,5, 5, TimeUnit.MINUTES);
        // Start subscription
        pubSubManager.subscribe(NODE_UPDATES_CHANNEL, this::refreshNode);
    }

    public void shutdown() {
        try {
            pubSubManager.unsubscribe(NODE_UPDATES_CHANNEL);
            // Shutdown scheduler
            this.scheduler.shutdown();

            if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                this.scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.scheduler.shutdownNow();
        }

        LOGGER.info("FreeSwitchRegistry shutdown complete");
    }


    // ================== CORE OPERATIONS ==================

    public FreeSwitchNode updateNodeStatus(String nodeId, Event event) {
        return performUpdate(nodeId, event);
    }

    public FreeSwitchNode updateNodeStatusWithLock(String nodeId, Event event) {
        if (!acquireLock(nodeId)) {
            throw new ConcurrentModificationException("Failed to acquire lock for " + nodeId);
        }

        try {
            return performUpdate(nodeId, event);
        } finally {
            releaseLock(nodeId);
        }
    }

    public FreeSwitchNode getNode(String nodeId) {
        FreeSwitchNode cached = localCache.getIfPresent(nodeId);
        if (cached != null) {
            return cached;
        }

        Map<Object, Object> hash = redisTemplate.opsForHash().entries(ALL_NODES_KEY + nodeId);
        if (hash != null && !hash.isEmpty()) {
            FreeSwitchNode node = FreeSwitchNode.fromRedisHash(hash);
            localCache.put(nodeId, node);
            return node;
        }
        return null;
    }

    private boolean acquireLock(String nodeId) {
        return Boolean.TRUE.equals(
                redisLockTemplate.opsForValue()
                        .setIfAbsent(LOCK_PREFIX + nodeId, "locked", LOCK_TIMEOUT)
        );
    }

    private boolean releaseLock(String nodeId) {
        return Boolean.TRUE.equals(
                redisLockTemplate.delete(LOCK_PREFIX + nodeId)
        );
    }

    private FreeSwitchNode performUpdate(String nodeId, Event event) {
        FreeSwitchNode node = getNode(nodeId);
        if (node == null) {
            node = createNewNode(nodeId, event);
        }

        node.updateStatus(event);
        saveNode(node);
        publishNodeUpdate(nodeId);
        return node;
    }

    // ================== SUPPORTING METHODS ==================
    private FreeSwitchNode createNewNode(String nodeId, Event event) {
        FreeSwitchNode node = new FreeSwitchNode(
                nodeId,
                event.getFreeSwitchHostname(),
                event.getIntParamWithDefault(EventParams.MAX_SESSIONS, 0)
        );
        node.setIpAddress(event.getFreeSwitchIpAddress());
        node.setLastUpdateTimestamp(Instant.now());
        return node;
    }

    private void saveNode(FreeSwitchNode node) {
        node.setLastUpdateTimestamp(Instant.now());
        localCache.put(node.getNodeId(), node);

        // Store as Redis Hash
        String redisKey = ALL_NODES_KEY + node.getNodeId();
        redisTemplate.opsForHash().putAll(redisKey, node.toRedisHash());

        // Update healthy nodes set
        if (node.isHealthy()) {
            stringRedisTemplate.opsForSet().add(HEALTHY_NODES_KEY, node.getNodeId());
        } else {
            stringRedisTemplate.opsForSet().remove(HEALTHY_NODES_KEY, node.getNodeId());
        }
    }

    private void publishNodeUpdate(String nodeId) {
        stringRedisTemplate.convertAndSend(NODE_UPDATES_CHANNEL, nodeId);
    }

    private void refreshNode(String nodeId) {
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(ALL_NODES_KEY + nodeId);
        if (hash != null && !hash.isEmpty()) {
            FreeSwitchNode node = FreeSwitchNode.fromRedisHash(hash);
            if (node != null) {
                localCache.put(nodeId, node);
            }
        }
    }

    // ================== PUBLIC QUERY METHODS ==================

    public List<FreeSwitchNode> getHealthyNodes() {
        Set<String> nodeIds = stringRedisTemplate.opsForSet().members(HEALTHY_NODES_KEY);
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Collections.emptyList();
        }

        return nodeIds.stream()
                .map(this::getNode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public FreeSwitchNode getLoadBalancedNode() {
        List<FreeSwitchNode> healthyNodes = getHealthyNodes().stream()
                .filter(FreeSwitchNode::isHealthy)
                .toList();

        if (healthyNodes.isEmpty()) {
            return null;
        }

        return healthyNodes.stream()
                .min(Comparator.comparingInt(FreeSwitchNode::getSessionCount))
                .orElse(null);
    }

    // ================== MAINTENANCE ==================

    private void cleanupInactiveNodes() {
        try {

            // Checks to avoid exceptions while shutdown
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("Cleanup aborted: thread interrupted before start.");
                return;
            }

            Set<String> keys = redisTemplate.keys(ALL_NODES_KEY + "*");
            if (keys.isEmpty()) return;

            int count = 0;
            for (String key : keys) {
                // Added checks to avoid exceptions while shutdown
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.info("Cleanup aborted: thread interrupted during processing.");
                    break;
                }

                try {
                    Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
                    if (hash != null && !hash.isEmpty()) {
                        FreeSwitchNode node = FreeSwitchNode.fromRedisHash(hash);
                        if (node != null && node.getLastUpdateTimestamp().isBefore(Instant.now().minus(2, ChronoUnit.MINUTES))) {
                            redisTemplate.delete(key);
                            stringRedisTemplate.opsForSet().remove(HEALTHY_NODES_KEY, node.getNodeId());
                            localCache.invalidate(node.getNodeId());
                            count++;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error processing key {}: {}", key, e.getMessage());
                    if (isCausedByInterruptedException(e)) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        LOGGER.warn("Cleanup task was interrupted during Redis operation");
                        break; // Stop further cleanup
                    }
                }
            }

            if (count > 0) {
                LOGGER.info("Cleaned up {} inactive nodes", count);
            }
        } catch (Exception exp) {
            if (isCausedByInterruptedException(exp)) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Cleanup task interrupted");
            } else {
                LOGGER.error("Failed to cleanup inactive nodes", exp);
            }
        }
    }

    private boolean isCausedByInterruptedException(Throwable ex) {
        while (ex != null) {
            if (ex instanceof InterruptedException) return true;
            ex = ex.getCause();
        }
        return false;
    }
}
