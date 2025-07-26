package com.onextel.CallServiceApplication.controller;

import com.onextel.CallServiceApplication.aop.RequireCallServiceUp;
import com.onextel.CallServiceApplication.dto.CallRequest;
import com.onextel.CallServiceApplication.dto.CallStatsResponse;
import com.onextel.CallServiceApplication.exception.NoAvailableFreeSwitchNodeException;
import com.onextel.CallServiceApplication.freeswitch.command.CallDropCause;
import com.onextel.CallServiceApplication.model.Call;
import com.onextel.CallServiceApplication.model.stats.CampaignStats;
import com.onextel.CallServiceApplication.model.stats.StandaloneStats;
import com.onextel.CallServiceApplication.service.CallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v1/api/calls")
@RequireCallServiceUp
@Slf4j
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @GetMapping("/serviceid")
    public String getServiceInstanceId() {
        return callService.getCallServiceInstanceId();
    }

    @PostMapping("/originate")
    public ResponseEntity<?> originateCall(
            @RequestBody CallRequest callRequest) throws NoAvailableFreeSwitchNodeException {
        // validate the request
        callRequest.validate();
        Call call = callService.originateCall(callRequest);
        return ResponseEntity.ok(call); // Return 200 OK
    }

    @GetMapping("/status/{callUuid}")
    public ResponseEntity<?> getCallStatus(@PathVariable String callUuid) {
        return callService.getCallStatus(callUuid)
                .map(response -> ResponseEntity.ok().body(response))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/hangup/{callUuid}")
    public ResponseEntity<Void> hangupCall(
            @PathVariable String callUuid,
            @RequestParam String fsNodeId,
            @RequestParam(required = false) String cause) throws NoAvailableFreeSwitchNodeException {
        CallDropCause dropCause = cause != null ? CallDropCause.fromString(cause) : CallDropCause.NORMAL_CLEARING;
        callService.hangupCall(callUuid, fsNodeId, dropCause);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/answer/{callUuid}")
    public ResponseEntity<Void> answerCall(
            @PathVariable String callUuid,
            @RequestParam String fsNodeId) throws NoAvailableFreeSwitchNodeException {
        callService.answerCall(callUuid, fsNodeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer/{callUuid}")
    public ResponseEntity<Void> transferCall(
            @PathVariable String callUuid,
            @RequestParam String destinationNumber,
            @RequestParam String fsNodeId,
            @RequestParam(required = false) String leg,   // -bleg or -both
            @RequestParam(required = false) String dialplan,  // Dialplan name (e.g., default)
            @RequestParam(required = false) String context  // Context name (e.g., default)
    ) throws NoAvailableFreeSwitchNodeException {
        callService.transferCall(callUuid, destinationNumber, fsNodeId, leg, dialplan, context);
        return ResponseEntity.ok().build();
    }


    /**
     * ====================== CALL STATS QUERY APIS ======================
     */

    @GetMapping("/stats/{callUuid}")
    public CompletableFuture<CallStatsResponse> getCallStats(
            @PathVariable String callUuid) {
        return callService.getCallStats(callUuid);
    }

    @GetMapping("/stats/campaign/{campaignId}")
    public CompletableFuture<CampaignStats> getCampaignStats(@PathVariable String campaignId) {
        return callService.getCampaignStats(campaignId);
    }

    @GetMapping("/stats/campaign/{campaignId}/instance/{instanceId}")
    public CompletableFuture<CampaignStats> getCampaignInstanceStats(
            @PathVariable String campaignId,
            @PathVariable String instanceId) {
        return callService.getCampaignInstanceStats(campaignId, instanceId);
    }

    @GetMapping("/stats/standalone")
    public CompletableFuture<StandaloneStats> getStandaloneStats() {
        return callService.getStandaloneStats();
    }

}



