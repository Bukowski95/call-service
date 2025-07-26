package com.onextel.CallServiceApplication.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisPersistenceConfig {

//    @Bean
//    public RedisConfiguration redisConfiguration() {
//        return new RedisConfiguration() {
//            @Override
//            public void configure(RedisServer server) {
//                // Snapshotting
//                server.setSave("900 1 300 10 60 10000");
//
//                // AOF Settings
//                server.setAppendOnly(true);
//                server.setAppendfsync("everysec");
//                server.setAutoAofRewritePercentage(100);
//                server.setAutoAofRewriteMinSize("64mb");
//            }
//        };
//    }
//
//    @Bean
//    public RedisKeyExpirationListener keyExpirationListener() {
//        return new RedisKeyExpirationListener();
//    }
}