package com.onextel.CallServiceApplication.config;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.EvictionConfig;
import org.apache.commons.pool2.impl.EvictionPolicy;

import java.time.Duration;
import java.time.Instant;

public class FiveMinuteIdleEvictionPolicy<T> implements EvictionPolicy<T> {
    private static final Duration MAX_IDLE_DURATION = Duration.ofMinutes(5);

    @Override
    public boolean evict(EvictionConfig config, PooledObject<T> underTest, int idleCount) {
        Instant lastReturn = underTest.getLastReturnInstant();
        Instant createInstant = underTest.getCreateInstant();
        Duration maxLifetime = Duration.ofHours(1);

        // Evict if connection is too old
        if (Duration.between(createInstant, Instant.now()).compareTo(maxLifetime) > 0) {
            return true;
        }

        // Evict if idle too long
        if (lastReturn != null) {
            return Duration.between(lastReturn, Instant.now()).compareTo(MAX_IDLE_DURATION) > 0;
        }
        return false;
    }
}