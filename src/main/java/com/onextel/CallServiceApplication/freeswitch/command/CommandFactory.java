package com.onextel.CallServiceApplication.freeswitch.command;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CommandFactory {

    public OriginateCommand createOriginateCommand(
            String callUrl, String extension, String applicationName,
            String applicationArguments, String dialplan, String context,
            String callerIdName, String callerIdNumber, int timeoutSec,
            Map<String, String> customVariables) {
        return new OriginateCommand(callUrl, extension,
                applicationName, applicationArguments, dialplan, context,
                callerIdName, callerIdNumber, timeoutSec, customVariables);
    }

    public HangupCommand createHangupCommand(String uuid,
                                             String cause,
                                             String correlationId) {
        return new HangupCommand(uuid, cause, correlationId);
    }

    public TransferCommand createTransferCommand(String uuid, String destination) {
        return new TransferCommand(uuid, destination);
    }

    public TransferCommand createTransferCommand(
            String uuid,
            String destination,
            String leg,
            String correlationId) {
        return new TransferCommand(uuid, destination, leg, correlationId);
    }

    public TransferCommand createTransferCommand(String uuid, String destination,
                                                 String leg, String dialplan,
                                                 String context, String correlationId) {
        return new TransferCommand(uuid, destination, leg,
                dialplan, context, correlationId);
    }

    public AnswerCommand createAnswerCommand(String uuid, String correlationId) {
        return new AnswerCommand(uuid, correlationId);
    }
}
