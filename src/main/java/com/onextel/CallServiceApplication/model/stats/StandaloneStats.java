package com.onextel.CallServiceApplication.model.stats;

import com.onextel.CallServiceApplication.model.CallState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.EnumMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StandaloneStats {
    private final Map<CallState, Long> stateCounts = new EnumMap<>(CallState.class);
    private long completed;
    private long failed;

    public StandaloneStats(Map<String, String> redisData) {
        redisData.forEach((key, value) -> {
            if (key.startsWith("count:")) {
                CallState state = CallState.valueOf(key.substring(6));
                stateCounts.put(state, Long.parseLong(value));
            } else if (key.equals("completed")) {
                this.completed = Long.parseLong(value);
            } else if (key.equals("failed")) {
                this.failed = Long.parseLong(value);
            }
        });
    }
}
