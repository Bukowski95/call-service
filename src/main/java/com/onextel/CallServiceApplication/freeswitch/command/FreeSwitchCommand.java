package com.onextel.CallServiceApplication.freeswitch.command;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// FreeSwitchCommand class to track command correlationId, replyToQueue, commandMessage
// and later response to command from FreeSwitch
@Getter
@Setter
public class FreeSwitchCommand {
    private final String correlationId;
    private final String replyToQueue;
    private final String commandMessage;
    private final CommandResponseCallback callback;
    private final Instant createdAt;
    private String commandResponse;


    public FreeSwitchCommand(
            String correlationId,
            String replyToQueue,
            String commandMessage,
            CommandResponseCallback callback
    ) {
        this.correlationId = correlationId;
        this.replyToQueue = replyToQueue;
        this.commandMessage = commandMessage;
        this.callback = callback;
        this.createdAt = Instant.now();
    }
}
