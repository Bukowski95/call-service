package com.onextel.CallServiceApplication.model.stats;

import com.onextel.CallServiceApplication.model.CallState;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.EnumMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class CampaignStats {
    private final String campaignId;
    private final String instanceId;
    private final Map<CallState, Long> stateCounts = new EnumMap<>(CallState.class);
    private long completed;
    private long failed;

    public CampaignStats(String campaignId, String instanceId, Map<String, String> redisData) {
        this.campaignId = campaignId;
        this.instanceId = instanceId;

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

    public long getTotalCalls() {
        return stateCounts.values().stream().mapToLong(Long::longValue).sum();
    }
}
