package com.onextel.CallServiceApplication.util;

import io.lettuce.core.api.async.RedisAsyncCommands;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RedisAsyncOperation<T> {
    CompletableFuture<T> execute(RedisAsyncCommands<String, String> asyncCommands) throws Exception;
}