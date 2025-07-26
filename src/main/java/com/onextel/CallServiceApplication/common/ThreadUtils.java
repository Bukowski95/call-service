package com.onextel.CallServiceApplication.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadUtils.class);

    public static boolean safeSleep(long millis) {
        boolean interrupted = false;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Restore the interrupt status so higher-level code can handle it
            Thread.currentThread().interrupt();
            LOGGER.warn("Thread was interrupted during sleep ({} ms)", millis, e);
            interrupted = true;
        }
        return interrupted;
    }

    public static boolean safeSleep(long millis, String context) {
        boolean interrupted = false;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Thread interrupted during sleep ({} ms) in context: {}", millis, context, e);
            interrupted = true;
        }
        return interrupted;
    }
}
