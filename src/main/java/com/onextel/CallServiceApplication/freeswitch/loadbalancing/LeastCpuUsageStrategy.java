package com.onextel.CallServiceApplication.freeswitch.loadbalancing;

import com.onextel.CallServiceApplication.freeswitch.FreeSwitchNode;

import java.util.Comparator;
import java.util.Map;

public class LeastCpuUsageStrategy implements LoadBalancingStrategy {
    @Override
    public FreeSwitchNode selectNode(Map<String, FreeSwitchNode> nodes) {
        return nodes.values().stream()
                .filter(FreeSwitchNode::isHealthy)
                .min(Comparator.comparingDouble(FreeSwitchNode::getCpuUsage))
                .orElse(null); // Return null if no node is healthy
    }
}