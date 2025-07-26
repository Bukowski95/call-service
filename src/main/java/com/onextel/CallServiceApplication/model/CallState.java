package com.onextel.CallServiceApplication.model;

import java.util.Set;

public enum CallState {
    IDLE,                           // Initial state
    RINGING,                        // Outbound: Dialing, Inbound: Ringing
    EARLY_MEDIA,                    // Early media detected
    ACTIVE,                         // Call answered and active
    TRANSFER_IN_PROGRESS,           // New state for attended transfers
    TRANSFERRED,                    // Transfer completed
    ON_HOLD,                        // Call on hold
    CONFERENCING,                   // Call in conference
    ENDED,                          // Normal termination
    FAILED,                         // Call failed
    TIMED_OUT;                      // No answer

    private Set<CallState> allowedTransitions;

    static {
        IDLE.allowedTransitions                 = Set.of(RINGING);
        RINGING.allowedTransitions              = Set.of(EARLY_MEDIA, ACTIVE, ON_HOLD, ENDED, FAILED, TIMED_OUT);
        EARLY_MEDIA.allowedTransitions          = Set.of(ACTIVE, ENDED, FAILED, ON_HOLD);
        ACTIVE.allowedTransitions               = Set.of(ON_HOLD, TRANSFER_IN_PROGRESS, CONFERENCING, ENDED, FAILED);
        TRANSFER_IN_PROGRESS.allowedTransitions = Set.of(ACTIVE, ON_HOLD, TRANSFERRED, ENDED, FAILED);
        TRANSFERRED.allowedTransitions          = Set.of(ENDED);
        ON_HOLD.allowedTransitions              = Set.of(ACTIVE, ENDED, FAILED, CONFERENCING);
        CONFERENCING.allowedTransitions         = Set.of(ACTIVE, ON_HOLD, ENDED, FAILED);
        ENDED.allowedTransitions                = Set.of();
        FAILED.allowedTransitions               = Set.of();
        TIMED_OUT.allowedTransitions            = Set.of();
    }

    public boolean canTransitionTo(CallState newState) {
        return allowedTransitions.contains(newState);
    }

    public void validateTransition(CallState newState) {
        if (!canTransitionTo(newState)) {
            throw new IllegalStateException(
                    String.format("Invalid transition from %s to %s", this, newState));
        }
    }

    @Override
    public String toString() {
        return this.name();  // Just return the enum constant name
    }

    public static CallState fromString(String state) {
        try {
            return CallState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unexpected value: " + state, e);
        }
    }

    public boolean isActive() {
        return this == ACTIVE || this == ON_HOLD ||
                this == CONFERENCING || this == TRANSFER_IN_PROGRESS;
    }

    public boolean isTerminal() {
        return this == ENDED || this == FAILED ||
                this == TIMED_OUT || this == TRANSFERRED;
    }

    public boolean isFailure() {
        return this == FAILED || this == TIMED_OUT;
    }

}
