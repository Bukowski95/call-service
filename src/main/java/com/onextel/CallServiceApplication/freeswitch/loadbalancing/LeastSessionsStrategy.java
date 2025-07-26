package com.onextel.CallServiceApplication.freeswitch.loadbalancing;



import com.onextel.CallServiceApplication.freeswitch.FreeSwitchNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public class LeastSessionsStrategy implements LoadBalancingStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(LeastSessionsStrategy.class);

    @Override
    public FreeSwitchNode selectNode(Map<String, FreeSwitchNode> nodes) {
        try {
            return nodes.values().stream()
                    .filter(Objects::nonNull)
                    .filter(FreeSwitchNode::isHealthy)
                    .min(Comparator.comparingInt(FreeSwitchNode::getSessionCount))
                    .orElse(null);
        } catch (Exception exp) {
            LOG.error("Error - {}", exp.getMessage());
        }
        return null;
    }
}
