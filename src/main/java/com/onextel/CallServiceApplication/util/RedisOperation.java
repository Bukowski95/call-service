package com.onextel.CallServiceApplication.util;


import io.lettuce.core.api.StatefulRedisConnection;

@FunctionalInterface
public interface RedisOperation<T> {
    T execute(StatefulRedisConnection<String, String> connection) throws Exception;
}