package com.onextel.CallServiceApplication.service;

import com.onextel.CallServiceApplication.audit.AuditEventType;
import com.onextel.CallServiceApplication.audit.AuditService;
import com.onextel.CallServiceApplication.common.startup.AppInstanceIdProvider;
import com.onextel.CallServiceApplication.dto.CallRequest;
import com.onextel.CallServiceApplication.dto.CallRequestUtils;
import com.onextel.CallServiceApplication.dto.CallStatsResponse;
import com.onextel.CallServiceApplication.exception.NoAvailableFreeSwitchNodeException;
import com.onextel.CallServiceApplication.freeswitch.FreeSwitchNode;
import com.onextel.CallServiceApplication.freeswitch.FreeSwitchRegistry;
import com.onextel.CallServiceApplication.freeswitch.command.*;
import com.onextel.CallServiceApplication.model.Call;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.stats.CampaignStats;
import com.onextel.CallServiceApplication.model.stats.StandaloneStats;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.onextel.CallServiceApplication.freeswitch.command.CustomVariables.*;

@Service
@Slf4j
public class CallService implements CommandResponseCallback {

    @Getter
    private final String callServiceInstanceId;
    private final AppInstanceIdProvider appInstanceIdProvider;
    private final FreeSwitchRegistry freeSwitchRegistry;
    private final CommandFactory commandFactory;
    private final CommandService commandService;
    private final CallManager callManager;
    private final AuditService auditService;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Autowired
    public CallService(
            AppInstanceIdProvider appInstanceIdProvider,
            CommandFactory commandFactory,
            FreeSwitchRegistry freeSwitchRegistry,
            CommandService commandService,
            CallManager callManager,
            AuditService auditService) {
        this.appInstanceIdProvider = appInstanceIdProvider;
        this.commandFactory = commandFactory;
        this.freeSwitchRegistry = freeSwitchRegistry;
        this.commandService = commandService;
        this.callManager = callManager;
        this.auditService = auditService;

        this.callServiceInstanceId = appInstanceIdProvider.getAppInstanceId();

        log.info("CallService instance created with ID:{}", callServiceInstanceId);
        auditService.logSystemEvent(
                AuditEventType.CALL_SERVICE_INSTANCE_REGISTERED,
                callServiceInstanceId,
                "Call service instance registered");

        callManager.setAppInstanceId(callServiceInstanceId);
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public void shutdown() {
        shuttingDown.set(true);
        log.info("CallService instance with ID: {} is being destroyed.", callServiceInstanceId);
        auditService.logSystemEvent(
                AuditEventType.CALL_SERVICE_INSTANCE_UNREGISTERED,
                callServiceInstanceId,
                "Call service instance unregistered");
    }

    public Call originateCall(CallRequest callRequest) throws NoAvailableFreeSwitchNodeException {
        Call newCall = CallRequestUtils.getCallObjectFromCallRequest(callRequest);
        newCall.updateCallState(CallState.IDLE);
        newCall.setCallServiceInstanceId(callServiceInstanceId);
        // try to get available freeswitch instance which has
        // the least number of sessions active
        FreeSwitchNode fsNode = freeSwitchRegistry.getLoadBalancedNode();
        if (fsNode == null) {
            String errorMessage = "No available FreeSwitch node to originate the call.";
            log.error(errorMessage);
            throw new NoAvailableFreeSwitchNodeException(errorMessage);
        }
        newCall.setFreeSwitchNodeId(fsNode.getNodeId());
        OriginateCommand callCommand =
                CallRequestUtils.getOriginateCommandFromCallRequest(callRequest);

        // Add application custom variables - to tack channel events later by ONEXTEL_CALL_ID
        callCommand.addCustomVariable(CORRELATION_ID, newCall.getCallUuid());
        callCommand.addCustomVariable(ONEXTEL_CALL_ID, newCall.getCallUuid());
        callCommand.addCustomVariable(ONEXTEL_CALL_SERVICE_ID, callServiceInstanceId);

        callManager.registerCall(newCall);
        String newCallCommandString = callCommand.toPlainText();
        commandService.sendMessageAsync(newCallCommandString, fsNode.getCommandQueueName(),
                newCall.getCallUuid(), this);
        return newCall;
    }

    public Optional<Call> getCallStatus(String uuid) {
        return callManager.getCall(uuid);
    }

    public void hangupCall(String uuid, String fsNodeId, CallDropCause cause) throws NoAvailableFreeSwitchNodeException {
        // verify that call exists by uuid
        FreeSwitchNode fsNode = freeSwitchRegistry.getNode(fsNodeId);
        if (fsNode == null) {
            // Unable to find FS instance
            String errorMessage= String.format("FreeSwitch node with id:[%s] is not available.", fsNodeId);
            log.error(errorMessage);
            throw new NoAvailableFreeSwitchNodeException(errorMessage);
        }
        HangupCommand hangupCommand = commandFactory.createHangupCommand(
                uuid, cause.getDescription(), uuid);
        String hangupCommandString = hangupCommand.toPlainText();
        commandService.sendMessageAsync(hangupCommandString, fsNode.getCommandQueueName(),
                uuid, this);
    }

    public void answerCall(String uuid, String fsNodeId) throws NoAvailableFreeSwitchNodeException {
        FreeSwitchNode fsNode = freeSwitchRegistry.getNode(fsNodeId);
        if (fsNode == null) {
            // Unable to find FS instance
            String errorMessage= String.format("FreeSwitch node with id:[%s] is not available.", fsNodeId);
            log.error(errorMessage);
            throw new NoAvailableFreeSwitchNodeException(errorMessage);
        }
        AnswerCommand answerCommand = commandFactory.createAnswerCommand(uuid, uuid);
        String answerCommandString = answerCommand.toPlainText();
        commandService.sendMessageAsync(answerCommandString, fsNode.getCommandQueueName(),
                uuid, this);
    }

    public void transferCall(String uuid, String destination, String fsNodeId,
                             String leg, String dialplan, String context) throws NoAvailableFreeSwitchNodeException {
        // verify that call exists by uuid
        FreeSwitchNode fsNode = freeSwitchRegistry.getNode(fsNodeId);
        if (fsNode == null) {
            // Unable to find FS instance
            String errorMessage= String.format("FreeSwitch node with id:[%s] is not available.", fsNodeId);
            log.error(errorMessage);
            throw new NoAvailableFreeSwitchNodeException(errorMessage);
        }
        TransferCommand transferCommand = commandFactory.createTransferCommand(
                uuid, destination, leg, dialplan, context, uuid);
        String transferCommandString = transferCommand.toPlainText();
        commandService.sendMessageAsync(transferCommandString, fsNode.getCommandQueueName(),
                uuid, this);
    }

    @Override
    public void onResponseReceived(String correlationId, FreeSwitchCommand commandInfo) {
        String callId = commandInfo.getCorrelationId();
        String command = commandInfo.getCommandMessage();
        String response = commandInfo.getCommandResponse();
        log.info("Command processed callId:{} Command:{} Response:{}",
                callId, command, response);
        // TODO: mark call as failed
    }

    public CompletableFuture<CallStatsResponse> getCallStats(String callUuid) {
        return callManager.getCallStats(callUuid);
    }

    public CompletableFuture<CampaignStats> getCampaignStats(String campaignId) {
        return callManager.getCampaignStats(campaignId);
    }

    public CompletableFuture<CampaignStats> getCampaignInstanceStats(String campaignId, String instanceId) {
        return callManager.getCampaignInstanceStats(campaignId, instanceId);
    }

    public CompletableFuture<StandaloneStats> getStandaloneStats() {
        return callManager.getStandaloneStats();
    }

}

