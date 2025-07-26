package com.onextel.CallServiceApplication.service.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.onextel.CallServiceApplication.exception.RedisOperationException;
import com.onextel.CallServiceApplication.util.RedisAsyncOperation;
import com.onextel.CallServiceApplication.util.RedisOperation;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.PoolException;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Component
@Slf4j
public class RedisConnectionPool {
    private final GenericObjectPool<StatefulRedisConnection<String, String>> redisLettucePool;

    public RedisConnectionPool(
            @Qualifier("redisLettuceConnectionPool") GenericObjectPool<StatefulRedisConnection<String, String>> redisLettucePool) {
        this.redisLettucePool = redisLettucePool;
    }

    public <T> T executeWithConnection(String operationName, RedisOperation<T> operation) {
        StatefulRedisConnection<String, String> connection = null;
        try {
            connection = redisLettucePool.borrowObject();
            return operation.execute(connection);
        } catch (Exception e) {
            log.error("Redis operation: {} failed: {}", operationName, e.getMessage());
            throw handleRedisException("Redis operation failed", e);
        } finally {
            returnConnectionToPool(connection);
        }
    }

    public <T> T executeSync(String operationName, Function<StatefulRedisConnection<String, String>, T> operation) {
        StatefulRedisConnection<String, String> connection = null;
        try {
            connection = redisLettucePool.borrowObject();
            return operation.apply(connection);
        } catch (Exception e) {
            log.error("Redis sync operation: {} failed: {}", operationName, e.getMessage());
            throw new RedisOperationException("Redis operation failed", e);
        } finally {
            returnConnectionToPool(connection);
        }
    }

    public <T> T executeSync(String operationName, RedisOperation<T> operation, T fallback) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = redisLettucePool.borrowObject();
            return operation.execute(conn);
        } catch (Exception e) {
            log.error("Redis operation {} failed", operationName, e);
            return fallback;
        } finally {
            if (conn != null) {
                redisLettucePool.returnObject(conn);
            }
        }
    }

    public <T> CompletableFuture<T> executeAsync(
            String operationName,
            Function<StatefulRedisConnection<String, String>, CompletionStage<T>> operation) {
        StatefulRedisConnection<String, String> connection = null;
        try {
            connection = redisLettucePool.borrowObject();
            CompletionStage<T> future = operation.apply(connection);

            // Ensure connection is returned when complete
            CompletableFuture<T> completable = future.toCompletableFuture();
            StatefulRedisConnection<String, String> finalConnection = connection;
            completable.whenComplete((r, e) ->  returnConnectionToPool(finalConnection));

            return completable;
        } catch (Exception e) {
            log.error("Redis operation {} failed", operationName, e);
            returnConnectionToPool(connection);
            return CompletableFuture.failedFuture(e);
        }
    }

    public <T> CompletableFuture<T> executeAsyncCommand(
            Function<RedisAsyncCommands<String, String>, CompletionStage<T>> operation) {
        StatefulRedisConnection<String, String> connection = null;
        try {
            connection = redisLettucePool.borrowObject();
            RedisAsyncCommands<String, String> commands = connection.async();

            CompletionStage<T> future = operation.apply(commands);
            CompletableFuture<T> completable = future.toCompletableFuture();

            // Create an effectively final reference for use in lambda
            final StatefulRedisConnection<String, String> finalConnection = connection;
            completable.whenComplete((r, e) -> returnConnectionToPool(finalConnection));

            return completable;
        } catch (Exception e) {
            returnConnectionToPool(connection);
            return CompletableFuture.failedFuture(e);
        }
    }

    // Unified execution method for asynchronous operations
    private <T> CompletableFuture<T> executeAsyncWithConnection(RedisAsyncOperation<T> operation) {
        StatefulRedisConnection<String, String> connection = null;
        try {
            connection = redisLettucePool.borrowObject();
            RedisAsyncCommands<String, String> async = connection.async();
            CompletableFuture<T> future = operation.execute(async).toCompletableFuture();

            // Create an effectively final reference for use in lambda
            final StatefulRedisConnection<String, String> finalConnection = connection;

            // Ensure connection is returned when complete
            future.whenComplete((result, error) ->
                    returnConnectionToPool(finalConnection)
            );

            return future;
        } catch (Exception e) {
            returnConnectionToPool(connection);
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(handleRedisException("Async Redis operation failed", e));
            return failed;
        }
    }

    // Async execution (for both Void and other return types)
    private CompletableFuture<Void> executeRedisAsyncOperation(
            String operationName,
            RedisAsyncOperation<Void> operation) {
        return executeAsyncWithConnection(async -> {
            try {
                CompletableFuture<?> future = operation.execute(async).toCompletableFuture();
                return future.thenApply(__ -> null);
            } catch (Exception e) {
                log.error("Redis method {} failed: {}", operationName, e.getMessage());
                throw e;
            }
        });
    }

    // Void-specific async convenience method
    private CompletableFuture<Void> executeVoidAsync(RedisAsyncOperation<?> operation) {
        return executeAsyncWithConnection(operation)
                .thenApply(__ -> null);
    }

    private void returnConnectionToPool(StatefulRedisConnection<String, String> conn) {
        if (conn != null) {
            try {
                redisLettucePool.returnObject(conn);
            } catch (Exception e) {
                log.warn("Error returning connection to pool", e);
            }
        }
    }

    private RuntimeException handleRedisException(String message, Exception e) {
        if (e instanceof RedisException) {
            log.error("Redis error: {}", e.getMessage());
            return new RedisOperationException(message, e);
        } else if (e instanceof JsonProcessingException) {
            log.error("Serialization error: {}", e.getMessage());
            return new SerializationException(message, e);
        } else if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            log.error("Operation interrupted");
            return new RedisOperationException(message, e);
        } else if (e instanceof PoolException) {
            log.error("Connection pool error: {}", e.getMessage());
            return new RedisConnectionException(message, e);
        }
        log.error("Unexpected error: {}", e.getMessage());
        return new RedisOperationException(message, e);
    }
}