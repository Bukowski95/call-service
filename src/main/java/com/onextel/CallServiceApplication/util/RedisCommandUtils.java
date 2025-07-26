package com.onextel.CallServiceApplication.util;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.IntegerOutput;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.output.ValueOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class RedisCommandUtils {

    // Async JSON Operations
    public static RedisFuture<String> jsonGetAsync(RedisAsyncCommands<String, String> async,
                                                   String key, String path) {
        return async.dispatch(
                CommandType.JSON_GET,
                new ValueOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(key).add(path));
    }

    public static RedisFuture<String> jsonSetAsync(RedisAsyncCommands<String, String> async,
                                                   String key, String path, String json) {
        return async.dispatch(
                CommandType.JSON_SET,
                new StatusOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(key).add(path).add(json));
    }

    // JSON.DEL operation
    public static RedisFuture<Long> jsonDelAsync(RedisAsyncCommands<String, String> async,
                                                 String key, String path) {
        return async.dispatch(
                CommandType.JSON_DEL,
                new IntegerOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(key).add(path));
    }

    public static RedisFuture<List<String>> jsonMGetAsync(
            RedisAsyncCommands<String, String> async,
            String path,
            String... keys) {

        RedisFuture<List<Object>> rawFuture = async.dispatch(
                CommandType.JSON_MGET,
                new NestedMultiOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .addKeys(keys)
                        .add(path));

        // a new CompletableFuture that transforms the result from List<Object> to List<String>
        CompletableFuture<List<String>> transformedFuture = rawFuture.toCompletableFuture()
                .thenApply(list -> list.stream()
                        .map(obj -> (String) obj) // Safe cast for JSON strings
                        .collect(Collectors.toList()));

        // Return as RedisFuture
        // Safe per Lettuce's implementation: RedisFuture wraps CompletableFuture
        @SuppressWarnings("unchecked")
        RedisFuture<List<String>> typedFuture = (RedisFuture<List<String>>) (RedisFuture<?>) transformedFuture;
        return typedFuture;
    }

    // Sync JSON Operations
    public static String jsonGetSync(RedisCommands<String, String> sync,
                                     String key, String path) {
        return sync.dispatch(
                CommandType.JSON_SET,
                new ValueOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(key).add(path));
    }

    public static String jsonSetSync(RedisCommands<String, String> sync,
                                     String key, String path, String json) {
        return sync.dispatch(
                CommandType.JSON_SET,
                new StatusOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(key).add(path).add(json));
    }


    // Set Operations
    public static RedisFuture<Long> sadd(RedisAsyncCommands<String, String> async,
                                         String key, String... members) {
        return async.sadd(key, members);
    }

    public static RedisFuture<Long> srem(RedisAsyncCommands<String, String> async,
                                         String key, String... members) {
        return async.srem(key, members);
    }

    // Hash Operations
    public static RedisFuture<Long> hincrby(RedisAsyncCommands<String, String> async,
                                            String key, String field, long amount) {
        return async.hincrby(key, field, amount);
    }

    // Key Operations
    public static RedisFuture<Boolean> expire(RedisAsyncCommands<String, String> async,
                                              String key, long seconds) {
        return async.expire(key, seconds);
    }
}