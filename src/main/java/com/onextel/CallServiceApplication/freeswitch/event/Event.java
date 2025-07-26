package com.onextel.CallServiceApplication.freeswitch.event;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;

@Getter
public class Event {
    private final EventType eventType;
    private final Map<String, Object> eventDetails;
    private final int eventSequence;

    public Event(Map<String, Object> eventDetails) {
        this.eventDetails = eventDetails;
        this.eventType = EventUtils.getEventType(eventDetails);
        if (this.eventType == null) {
            String eventDetailsFormatted = eventDetails.entrySet().stream()
                    .map(entry -> entry.getKey() +
                            " : " + entry.getValue())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Event type is missing or unknown. Event details: "
                    + eventDetailsFormatted);
        }
        this.eventSequence =  EventUtils.getIntParamWithDefault(
                eventDetails, EventParams.EVENT_SEQUENCE, 0);
    }

    public String getFreeSwitchNodeId() {
        return getStringParam(EventParams.CORE_UUID);
    }

    public String getFreeSwitchHostname() {
        return getStringParam(EventParams.FREESWITCH_HOSTNAME);
    }

    public String getFreeSwitchName() {
        return getStringParam(EventParams.FREESWITCH_SWITCHNAME);
    }

    public String getFreeSwitchIpAddress() {
        return getStringParam(EventParams.FREESWITCH_IPV4);
    }

    /**
     * Get Event-Date-Local as a LocalDateTime object.
     * Returns the date and time in the "YYYY-MM-DD HH:mm:ss" format (string).
     */
    public LocalDateTime getEventDateLocal() {
        String dateString = EventUtils.getStringParam(
                eventDetails, EventParams.EVENT_DATE_LOCAL);
        if (dateString != null) {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(dateString, formatter);
        }
        return null;
    }

    /**
     * Get Event-Date-GMT as a ZonedDateTime object (UTC time zone).
     * Returns the date and time in the "Day, DD Mon YYYY HH:mm:ss GMT" format (string).
     */
    public ZonedDateTime getEventDateGMT() {
        String dateString = EventUtils.getStringParam(
                eventDetails, EventParams.EVENT_DATE_GMT);
        if (dateString != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy HH:mm:ss 'GMT'");
            return ZonedDateTime.parse(dateString, formatter.withZone(ZoneOffset.UTC));
        }
        return null;
    }

    /**
     * Get Event-Date-Timestamp as an Instant object
     * Represents: The timestamp value representing the event date and time,
     * usually in epoch time (Unix timestamp), which is the number of
     * milliseconds (or seconds) since January 1, 1970, 00:00:00 UTC (the Unix epoch).
     */
     public Instant getEventDateTimestamp() {
        String timestamp = EventUtils.getStringParam(
                eventDetails, EventParams.EVENT_DATE_TIMESTAMP);
        if (timestamp != null) {
            try {
                long timestampMillis = Long.parseLong(timestamp);
                // Convert to Instant (milliseconds)
                return Instant.ofEpochMilli(timestampMillis);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid timestamp value: " + timestamp, e);
            }
        }
        return null;
    }

    public String getStringParam(String key) {
        return EventUtils.getStringParam(eventDetails, key);
    }

    public OptionalInt getIntParam(String key) {
        return EventUtils.getIntParam(eventDetails, key);
    }

    public int getIntParamWithDefault(String key, int defaultValue) {
        return EventUtils.getIntParamWithDefault(eventDetails, key, defaultValue);
    }

    public OptionalDouble getDoubleParam(String key) {
        return EventUtils.getDoubleParam(eventDetails, key);
    }

    public double getDoubleParamWithDefault(String key, double defaultValue) {
        return EventUtils.getDoubleParamWithDefault(eventDetails, key, defaultValue);
    }

    public String getParameter(String key) {
        String value = getStringParam(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter " + key + " is missing or null.");
        }
        return value;
    }

    @Override
    public String toString() {
        String lineSeparator = System.lineSeparator();
        // Format event details as key : value pairs
        String eventDetailsFormatted = eventDetails.entrySet().stream()
                .map(entry -> entry.getKey() + " : " + entry.getValue())
                .collect(Collectors.joining(lineSeparator));
        return String.format("event:%s Event-Sequence: %d:%s%s",
                eventType, eventSequence, lineSeparator, eventDetailsFormatted);
    }
}
