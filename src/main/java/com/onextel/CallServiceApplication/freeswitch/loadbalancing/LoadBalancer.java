package com.onextel.CallServiceApplication.freeswitch.loadbalancing;

import com.onextel.CallServiceApplication.freeswitch.FreeSwitchNode;

import java.util.Map;

public class LoadBalancer {
    private LoadBalancingStrategy strategy;

    // Available strategies
    public enum Strategy {
        LEAST_SESSIONS,
        ROUND_ROBIN,
        LEAST_CPU_USAGE,
    }

    public LoadBalancer(Strategy strategy) {
        setStrategy(strategy);
    }

    public void setStrategy(Strategy strategy) {
        switch (strategy) {
            case LEAST_SESSIONS:
                this.strategy = new LeastSessionsStrategy();
                break;
            case LEAST_CPU_USAGE:
                this.strategy = new LeastCpuUsageStrategy();
                break;
            case ROUND_ROBIN:
                this.strategy = new RoundRobinStrategy();
                break;
            default:
                throw new IllegalArgumentException("Unsupported strategy: " + strategy);
        }
    }

    public FreeSwitchNode getNextAvailableNode(Map<String, FreeSwitchNode> nodes) {
        // Use the selected strategy to pick a node
        return strategy.selectNode(nodes);
    }
}

// Instantiate LoadBalancer with the selected strategy
//LoadBalancer loadBalancer = new LoadBalancer(LoadBalancingStrategyType.LEAST_SESSIONS);
//
// Get the next available FreeSwitch node based on the selected strategy
//FreeSwitchNode selectedNode = loadBalancer.getNextAvailableNode(freeSwitchNodes);