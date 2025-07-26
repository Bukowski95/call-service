package com.onextel.CallServiceApplication.model;

import lombok.Getter;

import java.util.Set;

@Getter
public enum ChannelState {
    CREATING("CREATING"),       // Channel being created
    RINGING("RINGING"),        // Channel is ringing
    EARLY_MEDIA("EARLY_MEDIA"),    // Early media (progress)
    ANSWERED("ANSWERED"),       // Channel answered
    BRIDGED("BRIDGED"),        // Channel bridged to another
    UNBRIDGED("UNBRIDGED"),      // Channel was unbridged (transfer start)
    HELD("HELD"),               // Channel was held
    TRANSFERRING("TRANSFERRING"),   // New state for transfer in progress
    HANGUP("HANGUP"),         // Channel terminated
    FAILED("FAILED");         // Channel failed

    // Description of the channel's current state
    private final String stateDescription;
    private Set<ChannelState> allowedTransitions;

    ChannelState(String stateDescription) {
        this.stateDescription = stateDescription;
    }

    // Static initializer for transitions
    static {
        CREATING.allowedTransitions     = Set.of(RINGING, FAILED, HANGUP);
        RINGING.allowedTransitions      = Set.of(EARLY_MEDIA, ANSWERED, FAILED, HANGUP);
        EARLY_MEDIA.allowedTransitions  = Set.of(ANSWERED, FAILED, HANGUP);
        ANSWERED.allowedTransitions     = Set.of(BRIDGED, HELD, TRANSFERRING, HANGUP, FAILED);
        BRIDGED.allowedTransitions      = Set.of(UNBRIDGED, HELD, HANGUP, FAILED);
        UNBRIDGED.allowedTransitions    = Set.of(TRANSFERRING, ANSWERED, HANGUP, FAILED);
        HELD.allowedTransitions         = Set.of(ANSWERED, HANGUP, FAILED);
        TRANSFERRING.allowedTransitions = Set.of(HANGUP, FAILED);
        HANGUP.allowedTransitions       = Set.of();
        FAILED.allowedTransitions       = Set.of();
    }

    public static ChannelState fromString(String stateString) {
        try {
            return ChannelState.valueOf(stateString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown channel state: " + stateString, e);
        }
    }

    public boolean canTransitionTo(ChannelState newState) {
        return allowedTransitions.contains(newState);
    }

    public void validateTransition(ChannelState newState) {
        if (!canTransitionTo(newState)) {
            throw new IllegalStateException(
                    String.format("Invalid channel transition from %s to %s", this, newState));
        }
    }

    public boolean isActive() {
        return this == ANSWERED || this == BRIDGED || this == TRANSFERRING;
    }

    @Override
    public String toString() {
        return this.name();
    }
}