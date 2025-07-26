package com.onextel.CallServiceApplication.service.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.hash.Hashing;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfig;
import com.onextel.CallServiceApplication.model.webhook.WebhookConfigWithMetadata;
import com.onextel.CallServiceApplication.model.webhook.WebhookEventType;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 1. Primary Storage:
 *    wh:config:acct_123:https://example.com → Hash
 *      ├─ "config" → "{json}"
 *      └─ "createdAt" → "2023-07-20T12:00:00Z"
 *
 * 2. Account Index:
 *    wh:index:account:acct_123 → Se
 *      ├─ "https://example.com"
 *      └─ "https://backup.example.com"
 *
 * 3. Event Index:
 *    wh:index:event:CALL_STATE_CHANGE → Set
 *      ├─ "acct_123"
 *      └─ "acct_456"
 *
 * wh:config:{accountId}:{urlHash} → Config JSON
 * wh:index:account:{accountId} → Set<URLs>
 * wh:index:event:{eventType} → Set<AccountIDs>
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisWebhookRegistry  {
    private static final String KEY_PREFIX = "{wh}";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final StatefulRedisConnection<String, String> redisConnection;
    private final GenericObjectPool<StatefulRedisConnection<String, String>> connectionPool;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // Caffeine cache configuration
    private final Cache<String, WebhookConfigWithMetadata> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    // ========== Core Operations ========== //

    public CompletableFuture<Void> registerWebhook(String accountId, WebhookConfig config) {
        localCache.invalidate(getCacheKey(accountId, config.getUrl()));

        return executeAsync(commands -> {
            String serializedConfig = serializeConfig(config);
            Instant now = clock.instant();
            String eTag = generateETag(config);

            // 1. Store main config with metadata
            commands.hset(configKey(accountId, config.getUrl()),
                    Map.of(
                            "config", serializedConfig,
                            "createdAt", now.toString(),
                            "updatedAt", now.toString(),
                            "version", "1",
                            "eTag", eTag
                    ));
            commands.expire(configKey(accountId, config.getUrl()), DEFAULT_TTL);

            // 2. Update account index
            commands.sadd(accountIndexKey(accountId), config.getUrl());
            commands.expire(accountIndexKey(accountId), DEFAULT_TTL);

            // 3. Update event indexes
            List<RedisFuture<?>> futures = config.getSubscribedEvents().stream()
                    .map(event -> {
                        commands.sadd(eventIndexKey(event), accountId);
                        return commands.expire(eventIndexKey(event), DEFAULT_TTL);
                    })
                    .collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        });
    }

    public void updateWebhook(String accountId, WebhookConfig config) {
        localCache.invalidate(getCacheKey(accountId, config.getUrl()));
        redisConnection.sync().hset(
                configKey(accountId, config.getUrl()),
                "updatedAt", clock.instant().toString()
        );
        registerWebhook(accountId, config); // Re-register to update indexes
    }

    public void touch(String accountId, String url) {
        redisConnection.sync().expire(
                configKey(accountId, url),
                DEFAULT_TTL.getSeconds()
        );
    }

    public CompletableFuture<Void> unregisterWebhook(String accountId, String url) {
        localCache.invalidate(getCacheKey(accountId, url));
        return getWebhookConfig(accountId, url)
                .thenCompose(config -> {
                    if (config.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return executeAsync(commands -> {
                        // 1. Remove main config
                        commands.del(configKey(accountId, url));

                        // 2. Remove from account index
                        commands.srem(accountIndexKey(accountId), url);

                        // 3. Remove from event indexes
                        List<RedisFuture<?>> futures = config.get().getConfig().getSubscribedEvents().stream()
                                .map(event -> commands.srem(eventIndexKey(event), accountId))
                                .collect(Collectors.toList());

                        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    });
                });
    }

    // ========== Query Methods ========== //

    public CompletableFuture<Optional<WebhookConfigWithMetadata>> getWebhookConfig(String accountId, String url) {
        String cacheKey = getCacheKey(accountId, url);
        WebhookConfigWithMetadata cached = localCache.getIfPresent(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        return executeAsync(commands -> {
            CompletableFuture<Map<String, String>> redisFuture =
                    commands.hgetall(configKey(accountId, url)).toCompletableFuture();

            return redisFuture.thenApply(map -> {
                if (map == null || map.isEmpty()) {
                    return Optional.<WebhookConfigWithMetadata>empty();
                }

                try {
                    WebhookConfig config = objectMapper.readValue(map.get("config"), WebhookConfig.class);
                    WebhookConfigWithMetadata result = new WebhookConfigWithMetadata(
                            config,
                            Instant.parse(map.get("createdAt")),
                            Instant.parse(map.getOrDefault("updatedAt", map.get("createdAt"))),
                            map.get("version"),
                            map.get("eTag")
                    );
                    localCache.put(cacheKey, result);
                    return Optional.of(result);
                } catch (Exception e) {
                    log.error("Failed to deserialize webhook config", e);
                    return Optional.empty();
                }
            });
        });
    }


    public CompletableFuture<List<WebhookConfig>> getConfigsForAccount(String accountId) {
        return executeAsync(commands -> {
            CompletableFuture<Set<String>> urlsFuture =
                    commands.smembers(accountIndexKey(accountId)).toCompletableFuture();

            return urlsFuture.thenCompose(urls -> {
                List<CompletableFuture<Optional<WebhookConfigWithMetadata>>> configFutures = urls.stream()
                        .map(url -> getWebhookConfig(accountId, url))
                        .toList();

                return CompletableFuture.allOf(configFutures.toArray(new CompletableFuture[0]))
                        .thenApply(v -> configFutures.stream()
                                .map(CompletableFuture::join)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .map(WebhookConfigWithMetadata::getConfig)
                                .collect(Collectors.toList())
                        );
            });
        });
    }

    public CompletableFuture<List<WebhookConfig>> getConfigsForEvent(WebhookEventType eventType) {
        return executeAsync(commands -> {
            CompletableFuture<Set<String>> accountIdsFuture =
                    commands.smembers(eventIndexKey(eventType)).toCompletableFuture();

            return accountIdsFuture.thenCompose(accountIds -> {
                List<CompletableFuture<List<WebhookConfig>>> accountFutures = accountIds.stream()
                        .map(this::getConfigsForAccount)
                        .toList();

                return CompletableFuture.allOf(accountFutures.toArray(new CompletableFuture[0]))
                        .thenApply(v -> accountFutures.stream()
                                .flatMap(future -> future.join().stream())
                                .filter(config -> config.getSubscribedEvents().contains(eventType))
                                .collect(Collectors.toList())
                        );
            });
        });
    }

    public CompletableFuture<List<WebhookConfig>> getGlobalConfigs(WebhookEventType eventType) {
        return getConfigsForAccount("global")
                .thenApply(configs ->
                        configs.stream()
                                .filter(config -> config.getSubscribedEvents().contains(eventType))
                                .collect(Collectors.toList())
                );
    }

    // ========== ETag Support ========== //

    public CompletableFuture<Optional<WebhookConfigWithMetadata>> getIfModified(String accountId, String url, String clientETag) {
        return getWebhookConfig(accountId, url)
                .thenApply(optionalConfig ->
                        optionalConfig.filter(config -> !config.getETag().equals(clientETag))
                );
    }

    public CompletableFuture<Boolean> validateETag(String accountId, String url, String eTag) {
        return getWebhookConfig(accountId, url)
                .thenApply(optionalConfig ->
                        optionalConfig.map(config -> config.getETag().equals(eTag))
                                .orElse(false)
                );
    }

    // ========== Helper Methods ========== //

    private String generateETag(WebhookConfig config) {
        try {
            String configJson = objectMapper.writeValueAsString(config);
            return "\"" + Hashing.murmur3_128()
                    .hashString(configJson, StandardCharsets.UTF_8)
                    .toString() + "\"";
        } catch (JsonProcessingException e) {
            return "\"" + UUID.randomUUID() + "\"";
        }
    }

    private String getCacheKey(String accountId, String url) {
        return accountId + "|" + url.hashCode();
    }

    private String configKey(String accountId, String url) {
        return String.format("%s:config:%s:%d", KEY_PREFIX, accountId, url.hashCode());
    }

    private String accountIndexKey(String accountId) {
        return String.format("%s:idx:account:%s", KEY_PREFIX, accountId);
    }

    private String eventIndexKey(WebhookEventType eventType) {
        return String.format("%s:idx:event:%s", KEY_PREFIX, eventType.name());
    }

    private String serializeConfig(WebhookConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid webhook config", e);
        }
    }

    // ========== Connection Handling ========== //

    private <T> CompletableFuture<T> executeAsync(
            Function<RedisAsyncCommands<String, String>, CompletableFuture<T>> operation) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            RedisAsyncCommands<String, String> commands = conn.async();
            CompletableFuture<T> future = operation.apply(commands);
            StatefulRedisConnection<String, String> finalConn = conn;
            future.whenComplete((r, e) -> returnConnection(finalConn));
            return future;
        } catch (Exception e) {
            returnConnection(conn);
            return CompletableFuture.failedFuture(e);
        }
    }

    private void returnConnection(StatefulRedisConnection<String, String> conn) {
        if (conn != null) {
            try {
                connectionPool.returnObject(conn);
            } catch (Exception e) {
                log.warn("Error returning Redis connection", e);
            }
        }
    }


    // ========== Maintenance Methods ========== //

    @Scheduled(fixedRate = 86400000) // Daily cleanup
    public void cleanupExpiredWebhooks() {
        log.info("Running webhook cleanup");
        redisConnection.sync().keys(accountIndexKey("*")).forEach(accountKey -> {
            String accountId = accountKey.split(":")[3];
            String lastActive = redisConnection.sync().get(lastActivityKey(accountId));
            if (lastActive == null || Instant.parse(lastActive).isBefore(clock.instant().minus(DEFAULT_TTL))) {
                log.info("Cleaning up expired account: {}", accountId);
                unregisterAccount(accountId);
            }
        });
    }

    private void unregisterAccount(String accountId) {
        getConfigsForAccount(accountId).join().forEach(config ->
                unregisterWebhook(accountId, config.getUrl())
        );
    }

    private String lastActivityKey(String accountId) {
        return String.format("%s:activity:%s", KEY_PREFIX, accountId);
    }



}