package com.onextel.CallServiceApplication.freeswitch.event;

import com.onextel.CallServiceApplication.model.DTMFEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventUtils.class);

    // method to retrieve and convert EventType
    public static EventType getEventType(Map<String, Object> event) {
        String eventName = getEventParam(event, EventParams.EVENT_NAME, String.class);
        return eventName != null ? EventType.fromString(eventName) : null;
    }

    // Generic method to safely get a value and cast it to the desired type
    public static <T> T getEventParam(Map<String, Object> event, String key, Class<T> clazz) {
        Object value = event.get(key);
        if (value == null) {
            LOGGER.warn("Missing event parameter for key: {}", key);
        } else if (!clazz.isInstance(value)) {
            LOGGER.error("Invalid type for key: {}. Expected {} but got {}",
                    key, clazz.getSimpleName(), value.getClass().getSimpleName());
        }
        return clazz.cast(value);  // This will return null if the value is invalid
    }

    // Specific method for getting a String value from the event map
    public static String getStringParam(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    // Specific method for getting an Integer value from the event map
    public static OptionalInt getIntParam(Map<String, Object> event, String key) {
        Object value = event.get(key);

        // if the value is already an Integer
        if (value instanceof Integer) {
            return OptionalInt.of((Integer) value);
        }

        // If the value is a String and attempt to parse it to Integer
        if (value instanceof String) {
            try {
                return OptionalInt.of(Integer.parseInt((String) value));
            } catch (NumberFormatException e) {
                LOGGER.error("Unable to parse {} as Integer: {}", key, value);
                return OptionalInt.empty();
            }
        }

        return OptionalInt.empty();
    }

    public static int getIntParamWithDefault(Map<String, Object> event, String key, int defaultValue) {
        OptionalInt value = getIntParam(event, key);
        return value.orElse(defaultValue); // Return default if the value is empty
    }

    public static OptionalDouble getDoubleParam(Map<String, Object> event, String key) {
        Object value = event.get(key);

        // If the value is already a Double
        if (value instanceof Double) {
            return OptionalDouble.of((Double) value);
        }

        // if the value is a String and attempt to parse it to Double
        if (value instanceof String) {
            try {
                return OptionalDouble.of(Double.parseDouble((String) value));
            } catch (NumberFormatException e) {
                LOGGER.error("Unable to parse {} as Double: {}", key, value);
                return OptionalDouble.empty();
            }
        }

        return OptionalDouble.empty();
    }

    public static double getDoubleParamWithDefault(Map<String, Object> event, String key, double defaultValue) {
        OptionalDouble value = getDoubleParam(event, key);
        return value.orElse(defaultValue); // Return default if the value is empty
    }

    public static boolean isChannelEventByName(EventType eventType) {
        return eventType.name().startsWith("CHANNEL_") ||
                eventType == EventType.DTMF;
    }

    public static boolean isChannelEvent(EventType eventType) {
        return eventType == EventType.CHANNEL_CREATE ||
                eventType == EventType.CHANNEL_PROGRESS ||
                eventType == EventType.CHANNEL_ANSWER ||
                eventType == EventType.CHANNEL_HOLD ||
                eventType == EventType.CHANNEL_UNHOLD ||
                eventType == EventType.CHANNEL_HANGUP ||
                eventType == EventType.CHANNEL_HANGUP_COMPLETE ||
                eventType == EventType.CHANNEL_CALLSTATE ||
                eventType == EventType.CHANNEL_BRIDGE ||
                eventType == EventType.CHANNEL_UNBRIDGE ||
                eventType == EventType.DTMF;
    }

    public static String extractCorrelationId(String command) {
        if (command == null) {
            return null;
        }
        // Case 1: {correlation_id=xxx,...} at start (for originate)
        Pattern originatePattern = Pattern.compile("\\{.*?correlation_id=([^,}]+).*?\\}");
        Matcher originateMatcher = originatePattern.matcher(command);
        if (originateMatcher.find()) {
            return originateMatcher.group(1);
        }

        // Case 2: correlation_id:xxx at end (for other commands)
        Pattern standardPattern = Pattern.compile("correlation_id:([^\\s]+)$");
        Matcher standardMatcher = standardPattern.matcher(command);
        if (standardMatcher.find()) {
            return standardMatcher.group(1);
        }

        return null;
    }

    public static DTMFEventType determineEventType(String digit, int duration) {
        if (duration > 1000) {
            return DTMFEventType.LONG_PRESS;
        }
        return DTMFEventType.DIGIT_PRESSED;
    }
}