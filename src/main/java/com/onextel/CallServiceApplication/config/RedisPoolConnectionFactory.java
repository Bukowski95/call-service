package com.onextel.CallServiceApplication.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

// Custom connection factory
public class RedisPoolConnectionFactory extends BasePooledObjectFactory<StatefulRedisConnection<String, String>> {
    private final RedisClient redisClient;

    RedisPoolConnectionFactory(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public StatefulRedisConnection<String, String> create() {
        // Configure TCP keep alive and client options
        SocketOptions socketOptions = SocketOptions.builder()
                .keepAlive(true)              // Enable OS-level TCP keep alive
                .tcpNoDelay(true)             // Reduce latency (disable Nagle's algorithm)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)          // Reconnect on connection drops
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS) // Fail fast when disconnected
                .build();

        // Apply options to the client
        redisClient.setOptions(clientOptions);

        // Create connection and disable auto-flush (for batching)
        StatefulRedisConnection<String, String> connection = redisClient.connect();
        // TODO: default is AutoFlush true, disable after batching
        //connection.setAutoFlushCommands(false); // Manual flush for batch optimizations

        return connection;
    }

    @Override
    public PooledObject<StatefulRedisConnection<String, String>> wrap(StatefulRedisConnection<String, String> conn) {
        return new DefaultPooledObject<>(conn);
    }

    @Override
    public void destroyObject(PooledObject<StatefulRedisConnection<String, String>> p) {
        try {
            if (p.getObject() != null) {
                p.getObject().close(); // Gracefully close the connection
            }
        } catch (Exception e) {
            // Log warning
        }
    }

    @Override
    public boolean validateObject(PooledObject<StatefulRedisConnection<String, String>> p) {
        try {
            StatefulRedisConnection<String, String> conn = p.getObject();
            return conn != null
                    && conn.isOpen()
                    && !conn.isMulti() // Optional: Don't reuse connections in a MULTI block
                    && conn.sync().ping().equals("PONG"); // Test with a PING
        } catch (Exception e) {
            return false;
        }
    }
}
