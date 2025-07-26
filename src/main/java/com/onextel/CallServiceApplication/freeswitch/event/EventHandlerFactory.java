package com.onextel.CallServiceApplication.freeswitch.event;

import com.onextel.CallServiceApplication.audit.AuditService;
import com.onextel.CallServiceApplication.freeswitch.FreeSwitchRegistry;
import com.onextel.CallServiceApplication.freeswitch.event.handlers.*;
import com.onextel.CallServiceApplication.service.CallManager;
import com.onextel.CallServiceApplication.service.CommandService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventHandlerFactory {

    private final CommandService commandService;
    private final CallManager callManager;
    private final FreeSwitchRegistry clusterManager;
    private final AuditService auditService;
    private final Map<EventType, EventHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void initializeHandlers() {
        handlerMap.put(EventType.HEARTBEAT, new HeartbeatEventHandler(clusterManager));
        handlerMap.put(EventType.BACKGROUND_JOB, new BackgroundJobEventHandler(commandService));

        // Handling all Channel Events with the same handler
        handlerMap.put(EventType.CHANNEL_CREATE, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_PROGRESS, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_ANSWER, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_CALLSTATE, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_HANGUP, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_HANGUP_COMPLETE, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_BRIDGE, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_UNBRIDGE, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.DTMF, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_HOLD, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_UNHOLD, new ChannelEventHandler(callManager, auditService));
        handlerMap.put(EventType.CHANNEL_EXECUTE, new ChannelEventHandler(callManager, auditService));

        handlerMap.put(EventType.CUSTOM, new CustomEventHandler(callManager));

        log.info("EventHandlerFactory init success.");
    }

    public EventHandler getEventHandler(EventType eventType) {
        return handlerMap.getOrDefault(eventType, new FallbackEventHandler());
    }
}
