package com.onextel.CallServiceApplication.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "digit",
        "durationMs",
        "eventType",
        "source",
        "timestamp",
        "callUuid",
        "channelUuid"
})
public record DTMFEvent(
        @JsonProperty("digit") String digit,
        @JsonProperty("durationMs") int durationMs,
        @JsonProperty("eventType") DTMFEventType eventType,
        @JsonProperty("source") ChannelRole source,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("callUuid") String callUuid,
        @JsonProperty("channelUuid") String channelUuid) {

    // Compact constructor for validation
    public DTMFEvent {
        Objects.requireNonNull(digit, "digit cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(callUuid, "callUuid cannot be null");
        Objects.requireNonNull(channelUuid, "channelUuid cannot be null");
        durationMs = Math.max(0, durationMs);
    }

    // Factory method for creating with default timestamp
    public static DTMFEvent createWithCurrentTime(
            String digit,
            int durationMs,
            DTMFEventType eventType,
            ChannelRole source,
            String callUuid,
            String channelUuid) {
        return new DTMFEvent(
                digit,
                durationMs,
                eventType,
                source,
                Instant.now(),
                callUuid,
                channelUuid
        );
    }
}