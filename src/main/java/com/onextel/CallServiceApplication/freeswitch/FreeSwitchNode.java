package com.onextel.CallServiceApplication.freeswitch;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onextel.CallServiceApplication.freeswitch.event.Event;
import com.onextel.CallServiceApplication.freeswitch.event.EventParams;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FreeSwitchNode implements Serializable {
    // Timeout in milliseconds 30 seconds
    private static final long HEARTBEAT_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_CPU_USAGE_THRESHOLD = 80; // 80%
    // Use last 5 heartbeat events for CPU usage calculation
    private static final int HEARTBEAT_LIMIT_FOR_CPU_USAGE = 5;
    public static final String COMMAND_QUEUE_SUFFIX = "_command";

    @JsonProperty("nodeId")
    private final String nodeId;

    @JsonProperty("hostname")
    private final String hostname;

    @JsonProperty("maxSessionCount")
    private final int maxSessionCount;

    @JsonProperty("ipAddress")
    private String ipAddress;

    @JsonProperty("cpuUsage")
    private Double cpuUsage; // CPU usage in percentage

    @JsonProperty("sessionCount")
    private int sessionCount; // Number of active sessions

    @JsonProperty("lastUpdateTimestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastUpdateTimestamp;

    @JsonProperty("lastHeartbeatTime")
    private long lastHeartbeatTime;

    @JsonIgnore
    private transient Queue<Double> recentCpuUsages = new LinkedList<>();

    @JsonProperty("recentCpuUsages")
    public List<Double> getRecentCpuUsagesAsList() {
        return new ArrayList<>(recentCpuUsages);
    }

    @JsonProperty("recentCpuUsages")
    public void setRecentCpuUsagesFromList(List<Double> usages) {
        this.recentCpuUsages = new LinkedList<>(usages);
    }

    @JsonCreator // For JSON serialization
    protected FreeSwitchNode() {
        this.nodeId = "";
        this.hostname = "hostname";
        this.maxSessionCount = 1000; //default max sessions
    }

    public FreeSwitchNode(String nodeId, String hostname, int maxSessionCount) {
        this.nodeId = nodeId;
        this.hostname = hostname;
        this.maxSessionCount = maxSessionCount;
        this.cpuUsage = 0.0;
        this.sessionCount = 0;
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public void updateStatus(Event heartbeatEvent) {
        this.sessionCount = heartbeatEvent.getIntParamWithDefault(EventParams.SESSION_COUNT, 0);
        double idleCpuValue = heartbeatEvent.getDoubleParamWithDefault(EventParams.IDLE_CPU, 0.0);
        this.cpuUsage = 100 - idleCpuValue;
        // Add the current CPU usage to the queue
        if (recentCpuUsages.size() >= HEARTBEAT_LIMIT_FOR_CPU_USAGE) {
            // Remove the oldest value when the queue is full
            recentCpuUsages.poll();
        }
        // Add new CPU usage value
        recentCpuUsages.offer(cpuUsage);
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public boolean isInactive() {
        return (System.currentTimeMillis() - this.lastHeartbeatTime) > HEARTBEAT_TIMEOUT;
    }

    // Node is healthy if last heartbeat is within threshold
    // thresholdTime = 60000 (1 Minute or 30000 for 30 seconds)
    public boolean isInactive(long thresholdTime) {
        return (System.currentTimeMillis() - lastHeartbeatTime) <= thresholdTime;
    }

    // Node can handle load if CPU usage is below a threshold,
    // and it has room for more sessions
    public boolean isCpuUsageHigh() {
        int highUsageCount = 0;
        for (double usage : recentCpuUsages) {
            if (usage >= MAX_CPU_USAGE_THRESHOLD) {
                highUsageCount++;
            }
        }
        // If more than half of the last 5 events exceed the threshold,
        // mark as high cpu usage
        return highUsageCount >= HEARTBEAT_LIMIT_FOR_CPU_USAGE / 2;
    }

    public boolean isHealthy() {
        // Node is healthy if
        // 1. Current session count is less than maximum allowed sessions
        // 2. Average cpu usage is below current threshold (80%)
        // 3. FS node is actively sending heartbeat messages
        return this.sessionCount < this.maxSessionCount;
//        return this.sessionCount < this.maxSessionCount &&
//                !isCpuUsageHigh() &&
//                // received at least 1 heartbeat message within last one minute
//                !isInactive(60000);
    }

    public String getCommandQueueName() {
        return hostname + COMMAND_QUEUE_SUFFIX;
    }

    @Override
    public String toString() {
        return "FreeSwitchNode{" +
                "nodeId='" + nodeId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", maxSessionCount=" + maxSessionCount +
                ", ipAddress='" + ipAddress + '\'' +
                ", cpuUsage=" + cpuUsage +
                ", sessionCount=" + sessionCount +
                ", lastHeartbeatTime=" + lastHeartbeatTime +
                '}';
    }

    public Map<String, String> toRedisHash() {
        Map<String, String> hash = new HashMap<>();
        hash.put("nodeId", nodeId);
        hash.put("hostname", hostname);
        hash.put("maxSessionCount", String.valueOf(maxSessionCount));
        hash.put("ipAddress", ipAddress != null ? ipAddress : "");
        hash.put("cpuUsage", cpuUsage != null ? String.valueOf(cpuUsage) : "0.0");
        hash.put("sessionCount", String.valueOf(sessionCount));
        hash.put("lastUpdateTimestamp", lastUpdateTimestamp.toString());
        hash.put("lastHeartbeatTime", String.valueOf(lastHeartbeatTime));

        // Serialize recentCpuUsages as JSON array
        try {
            hash.put("recentCpuUsages", new ObjectMapper().writeValueAsString(getRecentCpuUsagesAsList()));
        } catch (JsonProcessingException e) {
            // TODO: Log error
        }

        return hash;
    }

    public static FreeSwitchNode fromRedisHash(Map<Object, Object> hash) {
        FreeSwitchNode node = new FreeSwitchNode(
                (String) hash.get("nodeId"),
                (String) hash.get("hostname"),
                Integer.parseInt((String) hash.get("maxSessionCount"))
        );

        node.setIpAddress((String) hash.get("ipAddress"));
        node.setCpuUsage(Double.parseDouble((String) hash.get("cpuUsage")));
        node.setSessionCount(Integer.parseInt((String) hash.get("sessionCount")));
        node.setLastUpdateTimestamp(Instant.parse((String) hash.get("lastUpdateTimestamp")));
        node.setLastHeartbeatTime(Long.parseLong((String) hash.get("lastHeartbeatTime")));

        // Deserialize recentCpuUsages
        try {
            List<Double> usages = new ObjectMapper().readValue(
                    (String) hash.get("recentCpuUsages"),
                    new TypeReference<List<Double>>() {}
            );
            node.setRecentCpuUsagesFromList(usages);
        } catch (Exception e) {
            // Handle deserialization error
        }

        return node;
    }
}
