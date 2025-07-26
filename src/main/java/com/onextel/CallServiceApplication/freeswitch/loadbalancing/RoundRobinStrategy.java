package com.onextel.CallServiceApplication.freeswitch.loadbalancing;

import com.onextel.CallServiceApplication.freeswitch.FreeSwitchNode;

import java.util.List;
import java.util.Map;

public class RoundRobinStrategy implements LoadBalancingStrategy {
    private int lastIndex = -1;

    @Override
    public FreeSwitchNode selectNode(Map<String, FreeSwitchNode> nodes) {
        List<FreeSwitchNode> healthyNodes = nodes.values().stream()
                .filter(FreeSwitchNode::isHealthy)
                .toList();

        if (healthyNodes.isEmpty()) {
            return null; // No healthy nodes available
        }

        // Round-robin selection of node
        lastIndex = (lastIndex + 1) % healthyNodes.size();
        return healthyNodes.get(lastIndex);
    }
}
