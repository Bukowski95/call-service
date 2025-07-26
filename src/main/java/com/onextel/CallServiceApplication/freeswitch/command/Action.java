package com.onextel.CallServiceApplication.freeswitch.command;

import lombok.Getter;

@Getter
public enum Action {
    ORIGINATE("originate"),
    HANGUP("hangup"),
    ANSWER("answer"),
    TRANSFER("transfer"),
    BROADCAST("broadcast");

    // Getter
    private final String action;

    // Constructor
    Action(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return action;
    }

    public static Action fromString(String action) {
        try {
            return Action.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unexpected value: " + action, e);
        }
    }
}
