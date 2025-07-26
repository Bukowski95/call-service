package com.onextel.CallServiceApplication.model;

import lombok.Getter;

@Getter
public enum ChannelRole {
    ORIGINATOR("ORIGINATOR", "Call initiator (A-leg)"),
    DESTINATION("DESTINATION", "Called party (B-leg)"),
    BRIDGED_LEG("BRIDGED_LEG", "Transferred or conference leg"),
    CONSULTATION("CONSULTATION", "Transfer consultation leg");
    // Description of the channel's current state
    private final String channelRole;
    private final String description;


    ChannelRole(String channelRole, String description) {
        this.channelRole = channelRole;
        this.description = description;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", channelRole, description);
    }

    public static ChannelRole fromString(String channelRole) {
        try {
            return ChannelRole.valueOf(channelRole);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown channel role: " + channelRole, e);
        }
    }
}









