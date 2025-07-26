package com.onextel.CallServiceApplication.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);

    public void logEvent(AuditEventType eventType, String callUuid, String channelUuid, String message) {
        String logMessage = String.format("[%s] Call: %s | Channel: %s | Msg: %s",
                eventType, callUuid, channelUuid, message);
        LOGGER.info(logMessage);
    }

    public void logEvent(AuditEventType eventType, int eventSequence, String callUuid, String channelUuid, String message) {
        String logMessage = String.format("[%s] Seq-Num: %d | Call: %s | Channel: %s | Msg: %s",
                eventType, eventSequence, callUuid, channelUuid, message);
        LOGGER.info(logMessage);
    }

    public void logDTMF(String callUuid, String channelUuid, String digit, int duration) {
        String message = String.format("Digit: %s, Duration: %d ms", digit, duration);
        logEvent(AuditEventType.DTMF_RECEIVED, callUuid, channelUuid, message);
    }

    public void logError(String callUuid, String channelUuid, String errorMessage, Exception ex) {
        String message = String.format("Error: %s | Exception: %s", errorMessage, ex.getMessage());
        LOGGER.error(message, ex);
        logEvent(AuditEventType.ERROR_OCCURRED, callUuid, channelUuid, message);
    }

    public void logCallStateChange(String callUuid, String newState) {
        String message = String.format("Call state changed to: %s", newState);
        logEvent(AuditEventType.CALL_STATE_CHANGED, callUuid, null, message);
    }

    public void logChannelStateChange(String callUuid, String channelUuid, String newState) {
        String message = String.format("Channel state changed to: %s", newState);
        logEvent(AuditEventType.CHANNEL_STATE_CHANGED, callUuid, channelUuid, message);
    }

    public void logCommandSent(String correlationId, String sendQueueName, String replyQueueName, String commandPayload) {
        String logMessage = String.format("[%s] correlationId: %s | Send Queue: %s | reply Queue: %s | Payload: %s",
                AuditEventType.COMMAND_SENT, correlationId, sendQueueName, replyQueueName, commandPayload);
        LOGGER.info(logMessage);
    }

    public void logCommandResponseReceived(String correlationId, String responsePayload) {
        String logMessage = String.format("[%s] correlationId: %s | Response: %s",
                AuditEventType.COMMAND_RESPONSE_RECEIVED,  correlationId, responsePayload);
        LOGGER.info(logMessage);
    }

    public void logSystemEvent(AuditEventType eventType, String instanceId,  String message) {
        String logMessage = String.format("[%s] instanceId: %s | Msg: %s",
                eventType, instanceId, message);
        LOGGER.info(logMessage);
    }
}
