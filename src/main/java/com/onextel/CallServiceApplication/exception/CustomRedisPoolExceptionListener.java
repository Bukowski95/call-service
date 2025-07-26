package com.onextel.CallServiceApplication.exception;

import io.lettuce.core.RedisException;
import org.apache.commons.pool2.SwallowedExceptionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// Custom exception listener
public class CustomRedisPoolExceptionListener implements SwallowedExceptionListener {
    private static final Logger log = LoggerFactory.getLogger(CustomRedisPoolExceptionListener.class);

    @Override
    public void onSwallowException(Exception e) {
        if (e instanceof RedisException) {
            log.error("Redis operation failed: {}", e.getMessage());
        } else if (e instanceof InterruptedException) {
            log.warn("Thread interrupted during Redis operation");
            Thread.currentThread().interrupt(); // Restore interrupt status
        } else {
            log.error("Unexpected exception in Redis pool", e);
        }
    }
}

