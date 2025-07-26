package com.onextel.CallServiceApplication.dto;

import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.stats.StateTransition;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CallStatsResponse {
    private String callUuid;
    private CallState currentState;
    private List<StateTransition> history;

    public CallStatsResponse(String callUuid, CallState currentState, List<StateTransition> history) {
        this.callUuid = callUuid;
        this.currentState = currentState;
        this.history = history;
    }
}
