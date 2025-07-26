package com.onextel.CallServiceApplication.util;

import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Component
public class RedisPubSubManager {

    private final RedisClient redisClient;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final RedisPubSubAsyncCommands<String, String> async;
    private final Map<String, Consumer<String>> channelHandlers = new ConcurrentHashMap<>();

    public RedisPubSubManager(RedisClient redisClient) {
        this.redisClient = redisClient;
        this.pubSubConnection = redisClient.connectPubSub();
        this.async = pubSubConnection.async();

        this.pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
                Consumer<String> handler = channelHandlers.get(channel);
                if (handler != null) {
                    handler.accept(message);
                }
            }
        });
    }

    public void subscribe(String channel, Consumer<String> handler) {
        channelHandlers.put(channel, handler);
        async.subscribe(channel);
    }

    public void unsubscribe(String channel) {
        channelHandlers.remove(channel);
        async.unsubscribe(channel);
    }

    @PreDestroy
    public void shutdown() {
        try {
            pubSubConnection.close();
            redisClient.shutdown();
        } catch (Exception e) {
            log.warn("Error closing Redis pub/sub connection", e);
        }
    }
}
