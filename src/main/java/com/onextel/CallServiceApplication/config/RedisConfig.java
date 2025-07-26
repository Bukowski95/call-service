package com.onextel.CallServiceApplication.config;

import com.onextel.CallServiceApplication.exception.CustomRedisPoolExceptionListener;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.ClientResources;
import lombok.NonNull;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.Callable;


@Configuration
public class RedisConfig {

    // Configuration properties
    private static final int POOL_MAX_WAIT_MS = 3000;
    private static final int REDIS_TIMEOUT_MS = POOL_MAX_WAIT_MS + 1000;
    private static final int POOL_EVICTION_RUN_INTERVAL_MS = 60000; // check every 1 minute
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 5000; //5 seconds

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password}")
    private String password;

    @Value("${spring.redis.lettuce.pool.max-active:50}")
    private int maxActive;

    @Value("${spring.redis.lettuce.pool.max-idle:20}")
    private int maxIdle;

    @Value("${spring.redis.lettuce.pool.min-idle:5}")
    private int minIdle;

    // ================== COMMON CONFIGURATION ==================
    private RedisStandaloneConfiguration baseRedisConfig() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (password !=null && !password.isEmpty()) {
            config.setPassword(password);
        }
        return config;
    }

    // ================== NON-REACTIVE CONFIGURATION ==================
    @Bean(destroyMethod = "close")
    @Primary
    public StatefulRedisConnection<String, String> statefulRedisConnection(
            GenericObjectPool<StatefulRedisConnection<String, String>> redisLettuceConnectionPool) {
        try {
            return redisLettuceConnectionPool.borrowObject();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Redis connection", e);
        }
    }

    @Bean
    @Primary
    public RedisConnectionFactory lettuceConnectionFactory() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(REDIS_TIMEOUT_MS))
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(true)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build())
                .clientResources(ClientResources.builder()
                        .ioThreadPoolSize(4)
                        .computationThreadPoolSize(4)
                        .build())
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(baseRedisConfig(), clientConfig);
        factory.setValidateConnection(true);
        factory.setShareNativeConnection(false); // Better for connection pooling
        return factory;
    }

    @Bean
    @Primary // used by FreeSwitchRegistry
    public RedisTemplate<String, Object> redisObjectTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());

        // Use Hash serializer for values
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }


    @Bean(name = "blockingRedisLockTemplate") // used by FreeSwitchRegistry
    public RedisTemplate<String, String> redisLockTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisLettuceClient() {
        RedisURI.Builder redisUriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(Duration.ofMillis(REDIS_TIMEOUT_MS));
                //.withSsl(useSsl);

        if (password != null && !password.isEmpty()) {
            redisUriBuilder.withPassword(password.toCharArray());
        }

        RedisClient client = RedisClient.create(redisUriBuilder.build());

        // Configure client options for better resilience
        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(DEFAULT_CONNECTION_TIMEOUT_MS))
                        .keepAlive(true)
                        .tcpNoDelay(true)
                        .build())
                .timeoutOptions(TimeoutOptions.builder()
                        .fixedTimeout(Duration.ofMillis(REDIS_TIMEOUT_MS))
                        .build())
                .build());

        return client;
    }

    @Bean(destroyMethod = "close")
    public GenericObjectPool<StatefulRedisConnection<String, String>> redisLettuceConnectionPool() {
        GenericObjectPoolConfig<StatefulRedisConnection<String, String>> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxActive);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWait(Duration.ofMillis(POOL_MAX_WAIT_MS));
        config.setTestOnBorrow(false);
        config.setTestOnCreate(false);
        config.setTestWhileIdle(true);
        config.setFairness(true); // Optional, based on contention
        config.setTimeBetweenEvictionRuns(Duration.ofMillis(POOL_EVICTION_RUN_INTERVAL_MS));
        config.setNumTestsPerEvictionRun(Math.max(1, maxIdle / 5)); // Test ~20% of idle connections per run
        config.setEvictionPolicy(new FiveMinuteIdleEvictionPolicy<>());

        GenericObjectPool<StatefulRedisConnection<String, String>> pool =
                new GenericObjectPool<>(new RedisPoolConnectionFactory(redisLettuceClient()), config);
        pool.setSwallowedExceptionListener(new CustomRedisPoolExceptionListener());
        return pool;
    }

    // ================== REACTIVE CONFIGURATION ==================
//    @Bean
//    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
//        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
//                .commandTimeout(Duration.ofMillis(REDIS_TIMEOUT_MS))
//                .build();
//        return new LettuceConnectionFactory(baseRedisConfig(), clientConfig);
//    }
//
//    @Bean
//    @Primary
//    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
//            ReactiveRedisConnectionFactory factory) {
//        return new ReactiveStringRedisTemplate(factory);
//    }
//
//    @Bean(name = "reactiveRedisLockTemplate")
//    public ReactiveRedisTemplate<String, String> reactiveRedisLockTemplate(
//            ReactiveRedisConnectionFactory factory) {
//        return new ReactiveRedisTemplate<>(factory, createLockSerializationContext());
//    }
//
//    @Bean
//    public ReactiveRedisTemplate<String, FreeSwitchNode> freeSwitchNodeTemplate(
//            ReactiveRedisConnectionFactory factory) {
//        return new ReactiveRedisTemplate<>(factory, nodeSerializationContext());
//    }
//
//    // ================== SERIALIZATION CONFIG ==================
//    private RedisSerializationContext<String, String> createLockSerializationContext() {
//        return RedisSerializationContext.<String, String>newSerializationContext()
//                .key(StringRedisSerializer.UTF_8)
//                .value(StringRedisSerializer.UTF_8)
//                .hashKey(StringRedisSerializer.UTF_8)
//                .hashValue(StringRedisSerializer.UTF_8)
//                .build();
//    }
//
//    private RedisSerializationContext<String, FreeSwitchNode> nodeSerializationContext() {
//        ObjectMapper mapper = new ObjectMapper()
//                .registerModule(new JavaTimeModule())
//                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//
//        Jackson2JsonRedisSerializer<FreeSwitchNode> serializer =
//                new Jackson2JsonRedisSerializer<>(mapper, FreeSwitchNode.class);
//
//        return RedisSerializationContext.<String, FreeSwitchNode>newSerializationContext()
//                .key(StringRedisSerializer.UTF_8)
//                .value(serializer)
//                .hashKey(StringRedisSerializer.UTF_8)
//                .hashValue(serializer)
//                .build();
//    }

    // ==================  TEMPLATE WITH RETRY CONFIGURATION ==================
    @Bean(name = "retryableRedisTemplate")
    public RedisTemplate<String, Object> retryableRedisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>() {
            @Override
            public <T> T execute(@NonNull RedisCallback<T> action, boolean exposeConnection, boolean pipeline) {
                return executeWithRetry(() -> super.execute(action, exposeConnection, pipeline));
            }
        };

        template.setConnectionFactory(lettuceConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        return template;
    }

    private <T> T executeWithRetry(Callable<T> redisOperation) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < DEFAULT_MAX_ATTEMPTS) {
            try {
                return redisOperation.call();
            } catch (RedisConnectionException e) {
                lastException = e;
                //log.warn("Redis connection failed, attempt {}/{}", attempts + 1, DEFAULT_MAX_ATTEMPTS, e);
                attempts++;
                try {
                    Thread.sleep(100L * attempts); // Exponential backoff would be better
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RedisSystemException("Interrupted during Redis operation", ie);
                }
            } catch (Exception e) {
                throw new RedisSystemException("Redis operation failed", e);
            }
        }

        throw new RedisSystemException("Redis operation failed after " + attempts + " attempts", lastException);
    }
}