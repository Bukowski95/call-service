package com.onextel.CallServiceApplication.dto;

import com.onextel.CallServiceApplication.freeswitch.command.OriginateCommand;
import com.onextel.CallServiceApplication.model.Call;

public class CallRequestUtils {
    public static Call getCallObjectFromCallRequest(CallRequest callRequest, String newCallUuid) {
        return new Call(newCallUuid,
                callRequest.getCallUrl(),
                callRequest.getCallerIdNumber(),
                callRequest.getCallerIdName(),
                callRequest.getExtension(),
                callRequest.getApplicationName(),
                callRequest.getApplicationArguments(),
                callRequest.getCustomVariables());
    }

    public static Call getCallObjectFromCallRequest(CallRequest callRequest) {
        return getCallObjectFromCallRequest(
                callRequest, java.util.UUID.randomUUID().toString());
    }

    public static OriginateCommand getOriginateCommandFromCallRequest(CallRequest callRequest) {
        return new OriginateCommand.Builder()
                .callUrl(callRequest.getCallUrl())
                .applicationName(callRequest.getApplicationName())
                .applicationArguments(callRequest.getApplicationArguments())
                .extension(callRequest.getExtension())
                .dialplan(callRequest.getDialplan())
                .context(callRequest.getContext())
                .callerIdName(callRequest.getCallerIdName())
                .callerIdNumber(callRequest.getCallerIdNumber())
                .timeoutSec(callRequest.getTimeoutSec())
                .customVariables(callRequest.getCustomVariables())
                .build();
    }
}
