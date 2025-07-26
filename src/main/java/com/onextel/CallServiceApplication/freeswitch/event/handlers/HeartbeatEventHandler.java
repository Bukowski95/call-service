package com.onextel.CallServiceApplication.freeswitch.event.handlers;

import com.onextel.CallServiceApplication.freeswitch.FreeSwitchRegistry;
import com.onextel.CallServiceApplication.freeswitch.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
    Event-Name : HEARTBEAT
    Core-UUID : bfed8de5-b7b6-419e-8f1f-319340058257
    FreeSWITCH-Hostname : OL03LTW-GNR0104
    FreeSWITCH-Switchname : OL03LTW-GNR0104
    FreeSWITCH-IPv4 : 172.22.22.86
    FreeSWITCH-IPv6 : ::1
    Event-Date-Local : 2025-03-05 19:55:59
    Event-Date-GMT : Wed, 05 Mar 2025 14:25:59 GMT
    Event-Date-Timestamp : 1741184759395375
    Event-Calling-File : switch_core.c
    Event-Calling-Function : send_heartbeat
    Event-Calling-Line-Number : 95
    Event-Sequence : 5838
    Event-Info : System Ready
    Up-Time : 0 years, 0 days, 7 hours, 56 minutes, 19 seconds, 865 milliseconds, 404 microseconds
    FreeSWITCH-Version : 1.10.12-release-10222002881-a88d069d6f+git~20240802T210227Z~a88d069d6f~64bit
    Uptime-msec : 28579865
    Session-Count : 0
    Max-Sessions : 1000
    Session-Per-Sec : 30
    Session-Per-Sec-Last : 0
    Session-Per-Sec-Max : 2
    Session-Per-Sec-FiveMin : 0
    Session-Since-Startup : 12
    Session-Peak-Max : 2
    Session-Peak-FiveMin : 0
    Idle-CPU : 98.966667
*/

public class HeartbeatEventHandler extends EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatEventHandler.class);
    private final FreeSwitchRegistry clusterManager;

    public HeartbeatEventHandler(FreeSwitchRegistry clusterManager) {
        this.clusterManager = clusterManager;
    }

    @Override
    public void handleEvent(Event event) {
        int eventSequence = event.getEventSequence();
        String fsHostname = event.getFreeSwitchHostname();
        String fsNodeId = event.getFreeSwitchNodeId();
        String fsIpAddress = event.getFreeSwitchIpAddress();
        try {
            // Handle heartbeat event and update session count or other relevant metrics
            LOG.info("Processing heartbeat event eventSequence [{}] host [{}] ip [{}] nodeId [{}]",
                    eventSequence, fsHostname, fsIpAddress, fsNodeId);
            clusterManager.updateNodeStatus(fsNodeId, event);
        } catch (Exception ex) {
            LOG.error("Failed to process heartbeat event for {} nodeId {}", fsHostname, fsNodeId, ex);
        }
    }
}
