package com.onextel.CallServiceApplication.freeswitch.event.handlers;

import com.onextel.CallServiceApplication.common.StringUtils;
import com.onextel.CallServiceApplication.freeswitch.command.CustomVariables;
import com.onextel.CallServiceApplication.freeswitch.event.Event;
import com.onextel.CallServiceApplication.freeswitch.event.EventParams;
import com.onextel.CallServiceApplication.model.Call;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.service.CallManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * "Event-Name":"CUSTOM",
 * "Core-UUID":"729b4be4-3899-4a6a-b1e1-8edc9dbe5ea0",
 * "FreeSWITCH-Hostname":"OL03LTW-GNR0104",
 * "FreeSWITCH-Switchname":"OL03LTW-GNR0104",
 * "FreeSWITCH-IPv4":"172.22.17.29",
 * "FreeSWITCH-IPv6":"::1",
 * "Event-Date-Local":"2025-04-02 15:02:16",
 * "Event-Date-GMT":"Wed, 02 Apr 2025 09:32:16 GMT",
 * "Event-Date-Timestamp":"1743586336698372",
 * "Event-Calling-File":"sofia_reg.c",
 * "Event-Calling-Function":"sofia_reg_handle_register_token",
 * "Event-Calling-Line-Number":"1606",
 * "Event-Sequence":"660",
 * "Event-Subclass":"sofia::register_attempt",
 * "profile-name":"internal",
 * "from-user":"103",
 * "from-host":"fs.onextel.com",
 * "contact":"\"103\" <sip:103@172.22.16.1:49962;rinstance=b9d4f909db1ca736;transport=UDP>",
 * "call-id":"Ch6tFdJUcul25v-ZZEsbrg..",
 * "rpid":"unknown",
 * "status":"Registered(UDP)",
 * "expires":"60",
 * "to-user":"103",
 * "to-host":"fs.onextel.com",
 * "network-ip":"172.22.16.1",
 * "network-port":"49962",
 * "username":"103",
 * "realm":"fs.onextel.com",
 * "user-agent":"Z 5.6.6 v2.10.20.5",
 * "auth-result":"RENEWED"
 */
public class CustomEventHandler extends EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CustomEventHandler.class);

    private final CallManager callManager;

    public CustomEventHandler(CallManager callManager) {
        this.callManager = callManager;
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.info("Custom Processing event: {} eventSequence {}",
                event.getEventType(), event.getEventSequence());
        try {
            String subclass = event.getStringParam(EventParams.EVENT_SUBCLASS);
            if ("conference::maintenance".equals(subclass)) {
                String callUuid = resolveCallUuid(event);
                if (StringUtils.isNullOrBlank(callUuid)) {
                    LOGGER.warn("No call UUID found for event type:{}-{}",
                            event.getEventType(), event);
                    return;
                }

                Optional<Call> activeCall = callManager.getCall(callUuid);
                if (activeCall.isEmpty()) {
                    LOGGER.warn("No active call found for event type:{}-{}",
                            event.getEventType(), event);
                    return;
                }
                handleConferenceEvent(activeCall.get(), event);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing FreeSWITCH event:{}", event, e);
        }
    }

    private void handleConferenceEvent(Call activeCall, Event event) {
        String action = event.getStringParam("Action");
        if ("add-member".equals(action)) {
            activeCall.updateCallState(CallState.CONFERENCING);
            LOGGER.info("Call added to conference: {}", activeCall.getCallUuid());
        }
    }

    private String resolveCallUuid(Event event) {
        // 1. Try direct variable
        String onexTelCallIdName = CustomVariables.getVariable(CustomVariables.ONEXTEL_CALL_ID);
        String callUuid = event.getStringParam(onexTelCallIdName);
        if (!StringUtils.isNullOrBlank(callUuid)) return callUuid;

        onexTelCallIdName = CustomVariables.getVariableWithSipHeader(CustomVariables.ONEXTEL_CALL_ID);
        callUuid = event.getStringParam(onexTelCallIdName);
        if (!StringUtils.isNullOrBlank(callUuid)) return callUuid;

        // 2. Try via channel UUID
        String channelUuid = event.getStringParam(EventParams.UNIQUE_ID);
        if (!StringUtils.isNullOrBlank(channelUuid)) {
            return callManager.getCallByChannel(channelUuid)
                    .map(Call::getCallUuid)
                    .orElse(null);
        }

        // 3. Try via other leg
        String otherLeg = event.getStringParam(EventParams.OTHER_LEG_UNIQUE_ID);
        if (!StringUtils.isNullOrBlank(otherLeg)) {
            return callManager.getCallByChannel(otherLeg)
                    .map(Call::getCallUuid)
                    .orElse(null);
        }
        return null;
    }

}
