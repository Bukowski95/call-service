package com.onextel.CallServiceApplication.freeswitch.event;

import lombok.Getter;

@Getter
public enum EventType {
    STARTUP("STARTUP"),
    HEARTBEAT("HEARTBEAT"),
    MODULE_LOAD("MODULE_LOAD"),
    MODULE_UNLOAD("MODULE_UNLOAD"),
    SHUTDOWN("SHUTDOWN"),
    DEL_SCHEDULE("DEL_SCHEDULE"),
    RE_SCHEDULE("RE_SCHEDULE"),
    CUSTOM("CUSTOM"),
    MESSAGE_QUERY("MESSAGE_QUERY"),
    UNPUBLISH("UNPUBLISH"),

    MEDIA_BUG_START("MEDIA_BUG_START"),
    MEDIA_BUG_STOP("MEDIA_BUG_STOP"),
    CODEC("CODEC"),
    REQUEST_PARAMS("REQUEST_PARAMS"),
    API("API"),
    BACKGROUND_JOB("BACKGROUND_JOB"),
    RECV_INFO("RECV_INFO"),
    CHANNEL_CALLSTATE("CHANNEL_CALLSTATE"),
    CHANNEL_STATE("CHANNEL_STATE"),
    CHANNEL_CREATE("CHANNEL_CREATE"),
    CHANNEL_HOLD("CHANNEL_HOLD"),
    CHANNEL_UNHOLD("CHANNEL_HOLD"),
    CHANNEL_EXECUTE("CHANNEL_EXECUTE"),
    CHANNEL_EXECUTE_COMPLETE("CHANNEL_EXECUTE_COMPLETE"),
    CHANNEL_ANSWER("CHANNEL_ANSWER"),
    PRESENCE_IN("PRESENCE_IN"),

    CHANNEL_HANGUP("CHANNEL_HANGUP"),
    CHANNEL_HANGUP_COMPLETE("CHANNEL_HANGUP_COMPLETE"),
    CHANNEL_DESTROY("CHANNEL_DESTROY"),
    CHANNEL_OUTGOING("CHANNEL_OUTGOING"),
    CHANNEL_BRIDGE("CHANNEL_BRIDGE"),
    CALL_UPDATE("CALL_UPDATE"),
    RECV_RTCP_MESSAGE("RECV_RTCP_MESSAGE"),
    CHANNEL_UNBRIDGE("CHANNEL_UNBRIDGE"),
    CHANNEL_ORIGINATE("CHANNEL_ORIGINATE"),
    CHANNEL_PROGRESS("CHANNEL_PROGRESS"),
    DTMF("DTMF"),
    PLAYBACK_START("PLAYBACK_START"),
    PLAYBACK_STOP("PLAYBACK_STOP"),
    RECORD_START("RECORD_START"),
    RECORD_STOP("RECORD_STOP");

    private final String eventType;

    // Constructor
    EventType(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public String toString() {
        return eventType;
    }

    public static EventType fromString(String eventType) {
        try {
            return EventType.valueOf(eventType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unexpected value: " + eventType, e);
        }
    }
}

