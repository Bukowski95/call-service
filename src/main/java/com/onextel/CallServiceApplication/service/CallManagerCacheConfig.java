package com.onextel.CallServiceApplication.service;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "call-manager.cache")
@Getter
@Setter
public class CallManagerCacheConfig {
    private long maxCalls = 10_000;
    private long maxChannels = 50_000;
    private Duration callExpireAfter = Duration.ofHours(1);
    private Duration channelExpireAfter = Duration.ofHours(1);
}


