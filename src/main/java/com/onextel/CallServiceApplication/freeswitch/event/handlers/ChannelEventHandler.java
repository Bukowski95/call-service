package com.onextel.CallServiceApplication.freeswitch.event.handlers;

import com.onextel.CallServiceApplication.audit.AuditEventType;
import com.onextel.CallServiceApplication.audit.AuditService;
import com.onextel.CallServiceApplication.freeswitch.command.CustomVariables;
import com.onextel.CallServiceApplication.freeswitch.event.Event;
import com.onextel.CallServiceApplication.freeswitch.event.EventParams;
import com.onextel.CallServiceApplication.freeswitch.event.EventUtils;
import com.onextel.CallServiceApplication.model.*;
import com.onextel.CallServiceApplication.service.CallManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * "Event-Name":"CHANNEL_CREATE",
 * "Core-UUID":"c7f8db4c-0894-495d-a7e6-91c89db72185",
 * "FreeSWITCH-Hostname":"OL03LTW-GNR0104",
 * "FreeSWITCH-Switchname":"OL03LTW-GNR0104",
 * "FreeSWITCH-IPv4":"172.22.22.86",
 * "FreeSWITCH-IPv6":"::1",
 * "Event-Date-Local":"2025-03-25 13:48:07",
 * "Event-Date-GMT":"Tue, 25 Mar 2025 08:18:07 GMT",
 * "Event-Date-Timestamp":"1742890687234535",
 * "Event-Calling-File":"switch_core_state_machine.c",
 * "Event-Calling-Function":"switch_core_session_run",
 * "Event-Calling-Line-Number":"626",
 * "Event-Sequence":"3302",
 * "Channel-State":"CS_INIT",
 * "Channel-Call-State":"DOWN",
 * "Channel-State-Number":"2",
 * "Channel-Name":"sofia/external/1003@onextel.com",
 * "Unique-ID":"20d34bd4-2fca-400f-9fbc-e67589f0676b",
 * "Call-Direction":"outbound",
 * "Presence-Call-Direction":"outbound",
 * "Channel-HIT-Dialplan":"false",
 * "Channel-Call-UUID":"20d34bd4-2fca-400f-9fbc-e67589f0676b",
 * "Answer-State":"ringing",
 * "Caller-Direction":"outbound",
 * "Caller-Logical-Direction":"outbound",
 * "Caller-Caller-ID-Name":"OneXTel Media Inc",
 * "Caller-Caller-ID-Number":"9876543210",
 * "Caller-Orig-Caller-ID-Name":"OneXTel Media Inc",
 * "Caller-Orig-Caller-ID-Number":"9876543210",
 * "Caller-Callee-ID-Name":"Outbound Call",
 * "Caller-Callee-ID-Number":"1003",
 * "Caller-ANI":"9876543210",
 * "Caller-Destination-Number":"1003",
 * "Caller-Unique-ID":"20d34bd4-2fca-400f-9fbc-e67589f0676b",
 * "Caller-Source":"src/switch_ivr_originate.c",
 * "Caller-Context":"default",
 * "Caller-Channel-Name":"sofia/external/1003@onextel.com",
 * "Caller-Profile-Index":"1",
 * "Caller-Profile-Created-Time":"1742890687234535",
 * "Caller-Channel-Created-Time":"1742890687234535",
 * "Caller-Channel-Answered-Time":"0",
 * "Caller-Channel-Progress-Time":"0",
 * "Caller-Channel-Progress-Media-Time":"0",
 * "Caller-Channel-Hangup-Time":"0",
 * "Caller-Channel-Transfer-Time":"0",
 * "Caller-Channel-Resurrect-Time":"0",
 * "Caller-Channel-Bridged-Time":"0",
 * "Caller-Channel-Last-Hold":"0",
 * "Caller-Channel-Hold-Accum":"0",
 * "Caller-Screen-Bit":"true",
 * "Caller-Privacy-Hide-Name":"false",
 * "Caller-Privacy-Hide-Number":"false",
 * "variable_direction":"outbound",
 * "variable_is_outbound":"true",
 * "variable_uuid":"20d34bd4-2fca-400f-9fbc-e67589f0676b",
 * "variable_call_uuid":"20d34bd4-2fca-400f-9fbc-e67589f0676b",
 * "variable_session_id":"19",
 * "variable_sip_local_network_addr":"172.22.22.86",
 * "variable_sip_profile_name":"external",
 * "variable_video_media_flow":"disabled",
 * "variable_text_media_flow":"disabled",
 * "variable_channel_name":"sofia/external/1003@onextel.com",
 * "variable_sip_destination_url":"sip:1003@onextel.com",
 * "variable_correlation_id":"1234567890",
 * "variable_origination_caller_id_name":"OneXTel Media Inc",
 * "variable_origination_caller_id_number":"9876543210",
 * "variable_originate_early_media":"true",
 * "variable_originate_endpoint":"sofia",
 * "variable_rtp_use_codec_string":"PCMU,PCMA",
 * "variable_local_media_ip":"172.22.22.86",
 * "variable_local_media_port":"23306",
 * "variable_advertised_media_ip":"172.22.22.86",
 * "variable_audio_media_flow":"sendrecv",
 * "variable_rtp_local_sdp_str":"v=0\r\no=FreeSWITCH 1742867381 1742867382 IN IP4 172.22.22.86\r\ns=FreeSWITCH\r\nc=IN IP4 172.22.22.86\r\nt=0 0\r\nm=audio 23306 RTP/AVP 0 8 101\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=rtpmap:101 telephone-event/8000\r\na=fmtp:101 0-15\r\na=silenceSupp:off - - - -\r\na=ptime:20\r\na=sendrecv\r\n",
 * "variable_sip_outgoing_contact_uri":"<sip:mod_sofia@172.22.22.86:5080>",
 * "variable_sip_req_uri":"1003@onextel.com",
 * "variable_sip_to_host":"onextel.com",
 * "variable_sip_from_host":"172.22.22.86",
 * "variable_sofia_profile_name":"external",
 * "variable_recovery_profile_name":"external",
 * "variable_sofia_profile_url":"sip:mod_sofia@172.22.22.86:5080"
 */
public class ChannelEventHandler extends EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            ChannelEventHandler.class);

    private final CallManager callManager;
    private final AuditService auditService;

    public ChannelEventHandler(CallManager callManager, AuditService auditService) {
        this.callManager = callManager;
        this.auditService = auditService;
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.info("Channel - Processing event: {} eventSequence {}", event.getEventType(), event.getEventSequence());

        String channelUuid = event.getStringParam(EventParams.UNIQUE_ID);
        Optional<String> callUuid = resolveCallUuid(event);

        if (callUuid.isEmpty()) {
            LOGGER.warn("No call UUID found for event type:{} {}", event.getEventType(), event);
            return;
        }

        Optional<Call> activeCall = callManager.getCall(callUuid.get());
        if (activeCall.isEmpty()) {
            LOGGER.warn("No active call found for event type:{} {}", event.getEventType(), event);
            handleMissingCall(channelUuid, event);
            return;
        }

        switch (event.getEventType()) {
            case CHANNEL_CREATE:
                handleChannelCreate(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_PROGRESS:
                handleChannelProgress(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_ANSWER:
                handleChannelAnswer(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_CALLSTATE:
                handleCallState(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_HANGUP:
                handleChannelHangup(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_HANGUP_COMPLETE:
                handleChannelHangupComplete(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_BRIDGE:
                handleChannelBridge(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_UNBRIDGE:
                handleChannelUnbridge(activeCall.get(), channelUuid, event);
                break;
            case DTMF:
                handleDTMF(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_HOLD:
                handleChannelHold(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_UNHOLD:
                handleChannelUnhold(activeCall.get(), channelUuid, event);
                break;
            case CHANNEL_EXECUTE:
                handleChannelExecute(activeCall.get(), channelUuid, event);
                break;
            default:
                LOGGER.warn("Unhandled channel event type: {}", event.getEventType());
        }
    }

    private Optional<String> resolveCallUuid(Event event) {
        List<String> possibleNames = Arrays.asList(
                CustomVariables.getVariable(CustomVariables.ONEXTEL_CALL_ID),
                CustomVariables.getVariableWithSipHeader(CustomVariables.ONEXTEL_CALL_ID)
        );

        for (String name : possibleNames) {
            String callUuid = event.getStringParam(name);
            if (callUuid != null) {
                return Optional.of(callUuid);
            }
        }

        // Try via channel UUIDs
        Optional<String> resolvedUuid = Stream.of(
                        event.getStringParam(EventParams.UNIQUE_ID),
                        event.getStringParam(EventParams.OTHER_LEG_UNIQUE_ID),
                        event.getStringParam(EventParams.CHANNEL_CALL_UUID)
                )
                .filter(Objects::nonNull)
                .map(this::resolveCallByChannel)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (resolvedUuid.isEmpty()) {
            LOGGER.warn("Failed to resolve call UUID for event type: {}. Event details: {}",
                    event.getEventType(), event);
        }

        return resolvedUuid;
    }

    private Optional<String> resolveCallByChannel(String channelUuid) {
        Optional<String> callUuid = callManager.getCallByChannel(channelUuid)
                .map(Call::getCallUuid);

        if (callUuid.isEmpty()) {
            LOGGER.debug("No active call found for channel UUID: {}", channelUuid);
        }

        return callUuid;
    }

    private ChannelRole determineChannelRole(Event event) {
        String direction = event.getStringParam(EventParams.CALL_DIRECTION);
        if (direction == null || direction.isEmpty()) {
            LOGGER.warn("Call direction is missing for event: {}", event);
            return ChannelRole.CONSULTATION; // Default to CONSULTATION if unclear
        }
        return switch (direction.toLowerCase()) {
            case "outbound" -> ChannelRole.ORIGINATOR;
            case "inbound" -> ChannelRole.DESTINATION;
            case "transfer" -> ChannelRole.CONSULTATION;
            case "bridge" -> ChannelRole.BRIDGED_LEG;
            default -> {
                LOGGER.warn("Unknown call direction: {} for event: {}", direction, event);
                yield ChannelRole.CONSULTATION;
            }
        };
    }

    private void handleChannelCreate(Call call, String channelUuid, Event event) {
        String callUuid = call.getCallUuid();
        ChannelRole role = determineChannelRole(event);
        Channel channel = new Channel(channelUuid, callUuid, role);

        // Set custom channel variables
        event.getEventDetails().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("variable_"))
                .forEach(entry -> {
                    String varName = entry.getKey().substring("variable_".length());
                    String varValue = entry.getValue() != null ? entry.getValue().toString() : null;
                    channel.getVariables().put(varName, varValue);
                });

        callManager.addChannelToCall(callUuid, channel);

        if (role == ChannelRole.ORIGINATOR) {
            callManager.updateCallState(call.getCallUuid(), CallState.RINGING);
        }

        auditService.logEvent(AuditEventType.CHANNEL_CREATED, callUuid, channelUuid,
                "Channel created with role: " + role);
        LOGGER.info("Channel created: {} for call {}", channelUuid, callUuid);
    }

    private void handleMissingCall(String channelUuid, Event event) {
        LOGGER.warn("Missing active call for channel UUID: {}. Event: {}", channelUuid, event);
        callManager.cleanupOrphanedChannel(channelUuid);
    }

    private void handleChannelProgress(Call call, String channelUuid, Event event) {
        call.getChannel(channelUuid).ifPresent(channel -> {
            String callUuid = call.getCallUuid();
            channel.setState(ChannelState.EARLY_MEDIA);
            if (event.getStringParam("Answer-State") != null) {
                call.setEarlyMediaDetected(true);
                LOGGER.info("Channel progress early media detected for channel:{} with call {}",
                        channelUuid, callUuid);
                auditService.logEvent(AuditEventType.EARLY_MEDIA, callUuid, channelUuid,
                        "Early media detected");
            }
        });
    }

    private void handleChannelAnswer(Call call, String channelUuid, Event event) {
        call.getChannel(channelUuid).ifPresent(channel -> {
            try {
                String callUuid = call.getCallUuid();
                // Validate both channel and call state transitions
                channel.getState().validateTransition(ChannelState.ANSWERED);
                call.getCurrentState().validateTransition(CallState.ACTIVE);

                channel.answer();
                callManager.updateCallState(callUuid, CallState.ACTIVE);
                LOGGER.info("Channel answered: {} for call {}", channelUuid, callUuid);
                auditService.logEvent(AuditEventType.CHANNEL_ANSWERED,
                    callUuid,
                    channelUuid,
                    "Channel answered after " +
                            Duration.between(call.getCreateTime(), Instant.now()).toSeconds() + "seconds"
                );
            } catch (IllegalStateException e) {
                LOGGER.error("Invalid state transition: {}", e.getMessage());
            }
        });
    }

    private void handleCallState(Call call, String channelUuid, Event event) {
        String callState = event.getStringParam(EventParams.CHANNEL_CALL_STATE);
        String callUuid = call.getCallUuid();
        call.getChannel(channelUuid).ifPresent(channel -> {
            channel.setDetailedState(callState);
            if ("HELD".equals(callState)) {
                callManager.updateCallState(callUuid, CallState.ON_HOLD);
            }
        });
        LOGGER.info("Channel state: {} channel {} call {}", callState, channelUuid, callUuid);
        auditService.logChannelStateChange(callUuid, channelUuid, callState);
    }

    private void handleChannelHangup(Call call, String channelUuid, Event event) {
        String hangupCause = event.getStringParam(EventParams.HANGUP_CAUSE);
        String callUuid = call.getCallUuid();
        call.getChannel(channelUuid).ifPresent(channel -> {
            channel.hangup(hangupCause);

            if ("NORMAL_CLEARING".equals(hangupCause)) {
                callManager.updateCallState(callUuid, CallState.ENDED);
            } else if ("NO_ANSWER".equals(hangupCause)) {
                callManager.updateCallState(callUuid, CallState.TIMED_OUT);
            } else {
                callManager.updateCallState(callUuid, CallState.FAILED);
            }
            LOGGER.info("Channel hung up: {} with cause {}", callUuid, hangupCause);
            auditService.logEvent(AuditEventType.CHANNEL_HANGUP,
                    callUuid,
                    channelUuid,
                    "Channel hung up with cause: " + hangupCause
            );
        });
    }

    private void handleChannelHangupComplete(Call call, String channelUuid, Event event) {
        if (call.allChannelsHangup()) {
            String callUuid = call.getCallUuid();
            callManager.finalizeCall(callUuid);
            LOGGER.info("call {} completed with duration {} ms", callUuid, call.getDuration().toMillis());
            auditService.logEvent(AuditEventType.CALL_COMPLETED,
                    callUuid,
                    channelUuid,
                    "Call completed with duration: " +
                            call.getDuration().toSeconds() + " seconds"
            );
        }
    }

    private void handleChannelBridge(Call call, String channelUuid, Event event) {
        String aLeg = event.getStringParam(EventParams.BRIDGE_A_UNIQUE_ID);
        String bLeg = event.getStringParam(EventParams.BRIDGE_B_UNIQUE_ID);

        call.getChannel(aLeg).ifPresent(Channel::bridge);
        call.getChannel(bLeg).ifPresent(Channel::bridge);

        String callUuid = call.getCallUuid();
        callManager.updateCallState(callUuid, CallState.ACTIVE);
        LOGGER.info("Channels bridged: {} and {}", aLeg, bLeg);
        auditService.logEvent(AuditEventType.CHANNEL_BRIDGED,
                callUuid,
                channelUuid,
                "Channels bridged: " + aLeg + " <-> " + bLeg
        );
    }

    private void handleChannelUnbridge(Call call, String channelUuid, Event event) {
        String aLeg = event.getStringParam(EventParams.BRIDGE_A_UNIQUE_ID);
        String bLeg = event.getStringParam(EventParams.BRIDGE_B_UNIQUE_ID);

        call.getChannel(aLeg).ifPresent(c -> c.setBridged(false));
        call.getChannel(bLeg).ifPresent(c -> c.setBridged(false));

        if (call.isBeingTransferred()) {
            callManager.updateCallState(call.getCallUuid(), CallState.TRANSFER_IN_PROGRESS);
        }
        LOGGER.info("Channels unbridged: {} and {}", aLeg, bLeg);

        auditService.logEvent(AuditEventType.CHANNEL_UNBRIDGED,
                call.getCallUuid(),
                channelUuid,
                "Channels unbridged: " + aLeg + " | " + bLeg
        );
    }

    private void handleDTMF(Call activeCall, String channelUuid, Event event) {
        String digit = event.getStringParam(EventParams.DTMF_DIGIT);
        int duration = event.getIntParamWithDefault(EventParams.DTMF_DURATION, 0);
        // Get the channel that received the DTMF
        activeCall.getChannel(channelUuid).ifPresent(channel -> {
            // Add DTMF to call history
            activeCall.addDTMFEvent(new DTMFEvent(
                    digit,
                    duration,
                    EventUtils.determineEventType(digit, duration),
                    channel.getChannelRole(),
                    event.getEventDateTimestamp(),
                    activeCall.getCallUuid(),
                    channelUuid
            ));
            LOGGER.info("DTMF {} received on call {} channel {} (duration: {} ms)",
                    digit, activeCall.getCallUuid(), channelUuid, duration);
            auditService.logDTMF(activeCall.getCallUuid(), channelUuid, digit, duration);
        });

    }

    private void handleChannelHold(Call call, String channelUuid, Event event) {
        call.getChannel(channelUuid).ifPresent(channel -> {
            String callUuid = call.getCallUuid();
            // Only allow hold from ANSWERED or BRIDGED states
            if (channel.getState() == ChannelState.ANSWERED ||
                    channel.getState() == ChannelState.BRIDGED) {
                channel.setState(ChannelState.HELD);
                callManager.updateCallState(callUuid, CallState.ON_HOLD);
                LOGGER.info("Channel put on hold: {} for call {}", channelUuid, callUuid);
                auditService.logEvent(AuditEventType.CHANNEL_HOLD,
                        callUuid,
                        channelUuid,
                        "Channel on hold"
                );
            }
        });
    }

    private void handleChannelUnhold(Call call, String channelUuid, Event event) {
        call.getChannel(channelUuid).ifPresent(channel -> {
            String callUuid = call.getCallUuid();
            if (channel.getState() == ChannelState.HELD) {
                channel.setState(ChannelState.ANSWERED);
                // If any channel is active, set call to ACTIVE
                boolean anyActive = call.getChannels().values().stream()
                        .anyMatch(Channel::isActive);
                callManager.updateCallState(callUuid, anyActive ? CallState.ACTIVE : CallState.RINGING);
                LOGGER.info("Channel unheld: {}", channelUuid);
                auditService.logEvent(AuditEventType.CHANNEL_UNHOLD,
                        callUuid,
                        channelUuid,
                        "Channel unheld"
                );
            }
        });
    }

    private void handleChannelExecute(Call activeCall, String channelUuid, Event event) {
        String application = event.getStringParam(EventParams.APPLICATION);
        String data = event.getStringParam(EventParams.APPLICATION_DATA);

        // Detect transfer completion
        if ("att_xfer".equals(application) || "transfer".equals(data)) {
            activeCall.updateCallState(CallState.TRANSFERRED);
            LOGGER.info("Call transferred: {}", activeCall.getCallUuid());
            auditService.logEvent(AuditEventType.CALL_TRANSFER_COMPLETE,
                    activeCall.getCallUuid(), channelUuid, "Call transferred");
        }
    }

}
