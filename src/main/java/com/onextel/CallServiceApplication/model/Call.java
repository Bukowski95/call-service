package com.onextel.CallServiceApplication.model;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)  // Avoid storing null values
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown properties when deserializing
public class Call {
    private final String callUuid;
    private final String callUrl;
    private final String callerIdName;
    private final String callerIdNumber;
    private final String extension;
    private final String applicationName;
    private final String applicationArguments;

    @JsonProperty("customVariables")
    private Map<String, String> customVariables = new HashMap<>();

    private String freeSwitchNodeId; // Which freeswitch node owns this
    private String callServiceInstanceId; // Which app instance owns this
    private boolean orphaned = false; // Whether call was abandoned

    // State information
    private CallState currentState = CallState.IDLE;
    private CallState previousState = CallState.IDLE;
    @JsonIgnore
    private boolean earlyMediaDetected = false;

    // Timing information
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastUpdateTimestamp;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createTime = Instant.now();

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant earlyMediaTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant answerTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant endTime;

    private Duration duration;

    private String hangupCause;

    // Channel management
    @JsonProperty("channels")
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    private String originatorChannelUuid;
    private String destinationChannelUuid;

    // Transfer specific
    @JsonIgnore
    private boolean beingTransferred = false;
    @JsonIgnore
    private String transferTarget;

    //DTMF specific
    @JsonProperty("dtmfHistory")
    private final List<DTMFEvent> dtmfHistory = Collections.synchronizedList(new ArrayList<>());

    @JsonCreator // For JSON serialization
    protected Call() {
        this.callUuid = "";
        this.callUrl = "";
        this.callerIdName = "";
        this.callerIdNumber = "";
        this.extension = "";
        this.applicationName = "";
        this.applicationArguments = "";
    }

    public Call(String callUuid, String callUrl, String callerIdNumber,
                String callerIdName, String extension,
                String applicationName, String applicationArguments,
                Map<String, String> customVariables) {
        this.callUuid = callUuid;
        this.callUrl = callUrl;
        this.callerIdNumber = callerIdNumber;
        this.callerIdName = callerIdName;
        this.extension = extension;
        this.applicationName = applicationName;
        this.applicationArguments = applicationArguments;
        this.customVariables = customVariables != null ?
                customVariables : new HashMap<>();
        this.currentState = CallState.IDLE; // Initial state
        this.previousState = CallState.IDLE;
        // Initial state
        updateTimeStamp();
    }

    public void updateCallState(CallState newState) {
        this.previousState = this.currentState;
        this.currentState = newState;

        switch(newState) {
            case ACTIVE:
                this.answerTime = Instant.now();
                break;
            case ENDED:
            case FAILED:
            case TIMED_OUT:
            case TRANSFERRED:
                this.endTime = Instant.now();
                this.duration = Duration.between(
                        this.answerTime != null ? this.answerTime : this.createTime,
                        this.endTime
                );
                break;
            case TRANSFER_IN_PROGRESS:
                this.beingTransferred = true;
                break;
        }
        updateTimeStamp();
    }

    public synchronized void addChannel(Channel channel) {
        channels.put(channel.getChannelUuid(), channel);

        // Set special channel references
        if (channel.getChannelRole() == ChannelRole.ORIGINATOR) {
            this.originatorChannelUuid = channel.getChannelUuid();
        } else if (channel.getChannelRole() == ChannelRole.DESTINATION) {
            this.destinationChannelUuid = channel.getChannelUuid();
        }
    }

    public Optional<Channel> getChannel(String channelUuid) {
        return Optional.ofNullable(channelUuid).map(channels::get);
    }

    public Optional<Channel> removeChannel(String channelUuid) {
        return Optional.ofNullable(channelUuid).map(channels::remove);
    }

    @JsonIgnore
    public Optional<Channel> getOriginatorChannel() {
        return Optional.ofNullable(originatorChannelUuid)
                .map(channels::get);
    }

    @JsonIgnore
    public Optional<Channel> getDestinationChannel() {
        return Optional.ofNullable(destinationChannelUuid)
                .map(channels::get);
    }

    @JsonIgnore
    public Optional<Channel> getOriginatorChannelByRole() {
        return channels.values().stream()
                .filter(c -> c.getChannelRole() == ChannelRole.ORIGINATOR)
                .findFirst();
    }

    @JsonIgnore
    public Optional<Channel> getDestinationChannelByRole() {
        return channels.values().stream()
                .filter(c -> c.getChannelRole() == ChannelRole.DESTINATION)
                .findFirst();
    }

    public boolean allChannelsHangup() {
        return channels.values().stream()
                .allMatch(c -> c.getState() == ChannelState.HANGUP);
    }

    public void initiateTransfer(String target) {
        this.beingTransferred = true;
        this.transferTarget = target;
        updateCallState(CallState.TRANSFER_IN_PROGRESS);
    }

    public void completeTransfer() {
        this.beingTransferred = false;
        updateCallState(CallState.TRANSFERRED);
    }

    public void setEarlyMediaDetected(boolean detected) {
        this.earlyMediaDetected = detected;
        if (detected) {
            this.earlyMediaTime = Instant.now();
        }
    }

    public boolean isActive() {
        return currentState == CallState.ACTIVE ||
                currentState == CallState.RINGING ||
                currentState == CallState.ON_HOLD ||
                currentState == CallState.CONFERENCING;
    }

    public boolean canHold() {
        return this.currentState == CallState.RINGING ||
                this.currentState == CallState.ACTIVE ||
                this.currentState == CallState.TRANSFER_IN_PROGRESS;
    }

    public Duration getDuration() {
        if (answerTime == null) return Duration.ZERO;
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(answerTime, end);
    }

    public void updateTimeStamp() {
        this.lastUpdateTimestamp = Instant.now();
    }

    public synchronized void addDTMFEvent(DTMFEvent dtmfEvent) {
        dtmfHistory.add(dtmfEvent);
    }

    public List<DTMFEvent> getDTMFHistory() {
        return Collections.unmodifiableList(dtmfHistory);
    }

}