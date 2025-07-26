package com.onextel.CallServiceApplication.freeswitch.loadbalancing;

import com.onextel.CallServiceApplication.freeswitch.FreeSwitchNode;

import java.util.Map;

public interface LoadBalancingStrategy {
    FreeSwitchNode selectNode(Map<String, FreeSwitchNode> nodes);
}
