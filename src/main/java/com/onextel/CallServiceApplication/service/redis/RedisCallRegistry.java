package com.onextel.CallServiceApplication.service.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onextel.CallServiceApplication.model.Call;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.Channel;
import com.onextel.CallServiceApplication.model.DTMFEvent;
import com.onextel.CallServiceApplication.util.RedisCommandUtils;
import com.onextel.CallServiceApplication.util.RedisJsonUtils;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.Range;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisCallRegistry {
    private final RedisConnectionPool connectionPool;
    private final ObjectMapper objectMapper;
    private String instanceId;

    // ========== CALL MANAGEMENT ========== //

    public synchronized void setAppInstanceId(String appInstanceId) {
        this.instanceId = appInstanceId;
    }

    public boolean registerCall(Call call) {
        return connectionPool.executeSync("registerCall", conn -> {
            try {
                RedisAsyncCommands<String, String> async = conn.async();
                String callJson = objectMapper.writeValueAsString(call);
                String callKey = RedisKeys.callKey(call.getCallUuid());
                String instanceCallsKey = RedisKeys.instanceCallsKey(instanceId);

                List<RedisFuture<?>> futures = new ArrayList<>(Arrays.asList(
                        RedisCommandUtils.jsonSetAsync(async, callKey, "$", callJson),
                        RedisCommandUtils.expire(async, callKey, RedisKeys.TTL.CALL_SECONDS),
                        RedisCommandUtils.sadd(async, instanceCallsKey, call.getCallUuid()),
                        RedisCommandUtils.sadd(async, RedisKeys.GLOBAL_CALLS_KEY, call.getCallUuid()),
                        RedisCommandUtils.hincrby(async, RedisKeys.GLOBAL_STATS_KEY, "totalCalls", 1),
                        RedisCommandUtils.hincrby(async, RedisKeys.GLOBAL_STATS_KEY, "activeCalls", 1)
                ));

                // Channel mappings
                call.getChannels().forEach((channelUuid, channel) -> {
                    futures.add(async.set(RedisKeys.channelMappingKey(channelUuid), call.getCallUuid()));
                    futures.add(async.expire(RedisKeys.channelMappingKey(channelUuid), RedisKeys.TTL.CALL_SECONDS));

                });

                return LettuceFutures.awaitAll(
                        RedisKeys.Intervals.BATCH_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                        futures.toArray(new RedisFuture[0])
                );
            } catch (JsonProcessingException e) {
                log.error("Serialization failed for call {} ", call.getCallUuid(), e);
                return false;
            }
        }, false); // Default fallback value
    }

    public boolean unregisterCall(String callUuid) {
        return connectionPool.executeSync("unregisterCall", conn -> {
            RedisAsyncCommands<String, String> async = conn.async();
            List<RedisFuture<?>> futures = Arrays.asList(
                    // Do not delete for now (24 hr ttl is set for all call keys)
                    //async.del(RedisKeys.callKey(callUuid)),
                    //async.srem(RedisKeys.instanceCallsKey(instanceId), callUuid),
                    //async.srem(RedisKeys.GLOBAL_CALLS_KEY, callUuid),

                    async.hincrby(RedisKeys.GLOBAL_STATS_KEY, "activeCalls", -1),
                    async.hincrby(RedisKeys.GLOBAL_STATS_KEY, "completedCalls", 1)
            );

            return LettuceFutures.awaitAll(
                    RedisKeys.Intervals.BATCH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                    futures.toArray(new RedisFuture[0])
            );
        }, false);
    }

    public boolean updateCall(Call call) {
        return connectionPool.executeSync("updateCall", conn -> {
            try {
                RedisAsyncCommands<String, String> async = conn.async();
                String callJson = objectMapper.writeValueAsString(call);
                List<RedisFuture<?>> futures = new ArrayList<>();

                futures.add(RedisCommandUtils.jsonSetAsync(
                        async,
                        RedisKeys.callKey(call.getCallUuid()),
                        "$",
                        callJson
                ));

                call.getChannels().forEach((channelUuid, channel) -> {
                    futures.add(async.set(
                            RedisKeys.channelMappingKey(channelUuid),
                            call.getCallUuid()
                    ));
                    futures.add(async.expire(RedisKeys.channelMappingKey(channelUuid), RedisKeys.TTL.CALL_SECONDS));
                });

                return LettuceFutures.awaitAll(
                        RedisKeys.Intervals.BATCH_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                        futures.toArray(new RedisFuture[0])
                );
            } catch (JsonProcessingException e) {
                log.error("Serialization failed for call {} ", call.getCallUuid(), e);
                return false;
            }
        }, false);
    }


    public Optional<Call> getCall(String callUuid) {
        return connectionPool.executeSync("getCall", conn -> {
            try {
                String json = RedisCommandUtils.jsonGetSync(conn.sync(),
                        RedisKeys.callKey(callUuid), "$");
                if (json == null) {
                    log.debug("Call {} not found in Redis", callUuid);
                    return Optional.empty();
                }
                return Optional.ofNullable(objectMapper.readValue(json, Call.class));
            } catch (JsonProcessingException exp) {
                log.error("Failed to get call {}", callUuid, exp);
                return Optional.empty();
            } catch (RedisCommandExecutionException e) {
                log.error("Redis command failed for call {}: {}", callUuid, e.getMessage());
                return Optional.empty();
            }
        }, Optional.empty());
    }

    public CompletableFuture<Void> updateCallStateWithTimestamp(String callUuid, CallState newState) {
        return connectionPool.executeAsync("updateCallStateWithTimestamp", conn ->
                RedisJsonUtils.updateCallStateWithTimestamp(
                                conn.async(),
                                RedisKeys.callKey(callUuid),
                                newState)
                        .toCompletableFuture()
                        .thenApply(__ -> null)
        );
    }

    public CompletableFuture<Void> addChannelToCall(String callUuid, Channel channel) {
        return connectionPool.executeAsync("addChannelToCall", conn ->
                {
                    try {
                        return RedisJsonUtils.addChannelToCall(
                                        conn.async(),
                                        RedisKeys.callKey(callUuid),
                                        channel.getChannelUuid(),
                                        channel)
                                .toCompletableFuture()
                                .thenApply(__ -> null);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to addChannelToCall the call {} channel {} ",
                                callUuid, channel.getChannelUuid(), e);
                        CompletableFuture<Void> failed = new CompletableFuture<>();
                        failed.completeExceptionally(e);
                        return failed;
                    }
                }
        );
    }

    public CompletableFuture<Void> addChannelsToCall(String callUuid, Map<String, Channel> channels) {
        if (channels.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return connectionPool.executeAsync("addChannelsToCall", conn -> {
            RedisAsyncCommands<String, String> async = conn.async();
            String callKey = RedisKeys.callKey(callUuid);

            List<RedisFuture<String>> futures = channels.entrySet().stream()
                    .map(entry -> {
                        try {
                            return RedisJsonUtils.addChannelToCall(
                                    async,
                                    callKey,
                                    entry.getKey(),
                                    entry.getValue());
                        } catch (JsonProcessingException e) {
                            log.error("Failed to addChannelToCall the call {} channel {} ",
                                    callUuid, entry.getKey(), e);
                            throw new CompletionException("Failed to serialize channel", e);
                        }
                    })
                    .toList();

            return CompletableFuture.allOf(
                    futures.stream()
                            .map(CompletionStage::toCompletableFuture)
                            .toArray(CompletableFuture[]::new)
            );
        });
    }

    public List<Call> getActiveCalls() {
        return connectionPool.executeSync("getActiveCalls", conn -> {
            Set<String> callUuids = conn.sync()
                    .smembers(RedisKeys.instanceCallsKey(instanceId));

            if (callUuids.isEmpty()) return List.of();

            RedisAsyncCommands<String, String> async = conn.async();

            List<CompletableFuture<String>> futures = callUuids.stream()
                    .map(uuid -> RedisCommandUtils.jsonGetAsync(async, RedisKeys.callKey(uuid), ".").toCompletableFuture())
                    .toList();

            CompletableFuture<Void> allFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            return allFuture.thenApply(v ->
                    futures.stream()
                            .map(this::safeParseCall)
                            .filter(Objects::nonNull)
                            .toList()
            ).join();
        }, List.of());
    }


    public List<Call> getOrphanedCalls() {
        return connectionPool.executeSync("getOrphanedCalls", conn -> {
            long cutoff = (System.currentTimeMillis() / 1000) - 90;
            Set<String> deadInstances = new HashSet<>(conn.sync()
                    .zrangebyscore(RedisKeys.HEARTBEAT_KEY, Range.create(Double.NEGATIVE_INFINITY, cutoff)));

            if (deadInstances.isEmpty()) return List.of();

            RedisAsyncCommands<String, String> async = conn.async();

            List<CompletableFuture<Set<String>>> uuidFutures = deadInstances.stream()
                    .map(instance -> async.smembers(RedisKeys.instanceCallsKey(instance)).toCompletableFuture())
                    .toList();

            // Combine UUID futures
            CompletableFuture<Void> allUuidFuture = CompletableFuture.allOf(uuidFutures.toArray(new CompletableFuture[0]));

            Set<String> orphanedUuids = allUuidFuture.thenApply(v ->
                    uuidFutures.stream()
                            .flatMap(fut -> safeJoinSet(fut).stream())
                            .collect(Collectors.toSet())
            ).join();

            if (orphanedUuids.isEmpty()) return List.of();

            List<CompletableFuture<String>> callFutures = orphanedUuids.stream()
                    .map(uuid -> RedisCommandUtils.jsonGetAsync(async, RedisKeys.callKey(uuid), ".").toCompletableFuture())
                    .toList();

            CompletableFuture<Void> allCallFuture = CompletableFuture.allOf(callFutures.toArray(new CompletableFuture[0]));

            return allCallFuture.thenApply(v ->
                    callFutures.stream()
                            .map(this::safeParseCall)
                            .filter(call -> call != null && call.isOrphaned())
                            .toList()
            ).join();
        }, List.of());
    }

    private Set<String> safeJoinSet(CompletableFuture<Set<String>> future) {
        try {
            return future.join();
        } catch (Exception e) {
            log.error("Failed to fetch UUIDs", e);
            return Set.of();
        }
    }

    private Call safeParseCall(CompletableFuture<String> future) {
        try {
            String json = future.join();
            return objectMapper.readValue(json, Call.class);
        } catch (Exception e) {
            log.error("Failed to parse Call JSON", e);
            return null;
        }
    }

    public Optional<String> getCallForChannel(String channelUuid) {
        return connectionPool.executeSync("getCallForChannel", conn -> {
            String callUuid = conn.sync().get(RedisKeys.channelMappingKey(channelUuid));
            return Optional.ofNullable(callUuid);
        }, Optional.empty());
    }

    // DTMF Operations
    public CompletableFuture<Void> appendDTMFEvent(String callUuid, DTMFEvent dtmfEvent) {
        return connectionPool.executeAsync("appendDTMF", conn ->
                {
                    try {
                        return RedisJsonUtils.appendDTMFEvent(
                                        conn.async(),
                                        RedisKeys.callKey(callUuid),
                                        dtmfEvent)
                                .toCompletableFuture()
                                .thenApply(__ -> null);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to append DTMF event {}  for call {}", dtmfEvent, callUuid, e);
                        return CompletableFuture.failedFuture(e);

                    }
                }
        );
    }

    public CompletableFuture<Void> clearDTMFHistory(String callUuid) {
        try {
            return connectionPool.executeAsync("clearDTMF", conn ->
                    RedisJsonUtils.clearDtmfHistory(
                                    conn.async(),
                                    RedisKeys.callKey(callUuid))
                            .toCompletableFuture()
                            .thenApply(__ -> null)
            );
        } catch (Exception e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public CompletableFuture<Void> addDTMFSequence(String callUuid, List<DTMFEvent> dtmfEvents) {
        if (dtmfEvents.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return connectionPool.executeAsync("addDTMFSequence", conn -> {
            RedisAsyncCommands<String, String> async = conn.async();
            String callKey = RedisKeys.callKey(callUuid);

            List<CompletableFuture<Void>> futures = dtmfEvents.stream()
                    .map(event -> {
                        try {
                            return RedisJsonUtils.appendDTMFEvent(async, callKey, event)
                                    .toCompletableFuture()
                                    .<Void>thenApply(__ -> null)
                                    .exceptionally(ex -> {
                                        log.error("Failed to append DTMF event", ex);
                                        return null;
                                    });
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize DTMF event", e);
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                    })
                    .toList();

            return CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
        });
    }
}
