package com.onextel.CallServiceApplication.freeswitch.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseCommand {
    public static final String DEFAULT_DIALPLAN = "default";
    public static final String DEFAULT_CONTEXT = "public";
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    // The action type, e.g., "originate"
    protected Action action;

    public BaseCommand(Action action) {
        this.action = action;
    }

    public String toPlainText() {
        return this.getAction().toString();
    }

    // Helper method to wrap in single quotes if the string contains spaces
    protected String wrapIfContainsSpace(String value) {
        return value.contains(" ") ? "'" + value + "'" : value;
    }
}
