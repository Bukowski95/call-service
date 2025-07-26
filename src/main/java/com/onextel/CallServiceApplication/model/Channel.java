package com.onextel.CallServiceApplication.model;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Channel {
    private String channelUuid; // FreeSWITCH channel UUID
    private String callUuid;  // Link this channel to a call
    private ChannelState state = ChannelState.CREATING;

    @JsonIgnore
    private String detailedState;
    private ChannelRole channelRole; // ORIGINATOR, DESTINATION, BRIDGED_LEG

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdTime = Instant.now();

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant answeredTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant hangupTime;

    private String hangupCause;
    private boolean bridged = false;

    @JsonProperty("variables")
    @JsonIgnore
    private Map<String, String> variables = new HashMap<>();


    @JsonCreator // For JSON serialization
    protected Channel() {
        this.channelUuid = "";
    }

    public Channel(String channelUuid,  String callUuid, ChannelRole channelRole) {
        this.channelUuid = channelUuid;
        this.callUuid = callUuid;
        this.channelRole = channelRole;
        this.createdTime = Instant.now();
    }

    public void answer() {
        this.state = ChannelState.ANSWERED;
        this.answeredTime = Instant.now();
    }

    public void bridge() {
        this.state = ChannelState.BRIDGED;
    }

    public void unbridge() {
        this.state = ChannelState.UNBRIDGED;
        this.bridged = false;
    }

    public void startTransfer() {
        this.state = ChannelState.TRANSFERRING;
    }

    public void hangup(String cause) {
        this.state = ChannelState.HANGUP;
        this.hangupCause = cause;
        this.hangupTime = Instant.now();
    }

    public void fail(String reason) {
        this.state = ChannelState.FAILED;
        this.hangupCause = reason;
        this.hangupTime = Instant.now();
    }

    public boolean isActive() {
        return state == ChannelState.ANSWERED ||
                state == ChannelState.BRIDGED;
    }

    public Duration getDuration() {
        if (answeredTime == null) {
            return Duration.ZERO;
        }
        Instant end = hangupTime != null ? hangupTime : Instant.now();
        return Duration.between(answeredTime, end);
    }
}
