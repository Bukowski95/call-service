package com.onextel.CallServiceApplication.model.stats;

import com.onextel.CallServiceApplication.model.CallState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StateTransition {
    private CallState state;
    private long timestamp;
    private String initiatedBy;
}
