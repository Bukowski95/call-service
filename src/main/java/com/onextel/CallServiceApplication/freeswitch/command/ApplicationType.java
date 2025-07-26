package com.onextel.CallServiceApplication.freeswitch.command;

import lombok.Getter;

@Getter
public enum ApplicationType {

    BRIDGE("bridge"),
    PLAYBACK("playback"),
    HANGUP("hangup"),
    ANSWER("answer"),
    TRANSFER("transfer"),
    SET("set"),
    JAVASCRIPT("javascript"),
    LUA("lua"),
    MENU("menu"),
    IVR("ivr"),
    CONFERENCE("conference"),
    RECORD("record"),
    SEND_DTMF("send_dtmf"),
    CALLCENTER("callcenter"),
    VOICEMAIL("voicemail"),
    DPTOOLS("dptools"),
    WAIT("wait"),
    SETVAR("setvar");

    private final String name;

    ApplicationType(String name) {
        this.name = name;
    }

    public static boolean isValid(String appName) {
        try {
            ApplicationType.valueOf(appName.toUpperCase()); // valueOf is case-sensitive
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static ApplicationType fromString(String applicationType) {
        try {
            return ApplicationType.valueOf(applicationType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unexpected value: " + applicationType, e);
        }
    }


}
