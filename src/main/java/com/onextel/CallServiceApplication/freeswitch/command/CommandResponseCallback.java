package com.onextel.CallServiceApplication.freeswitch.command;

public interface CommandResponseCallback {
    void onResponseReceived(String correlationId, FreeSwitchCommand commandInfo);
}
