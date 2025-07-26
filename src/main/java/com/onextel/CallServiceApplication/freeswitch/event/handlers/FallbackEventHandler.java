package com.onextel.CallServiceApplication.freeswitch.event.handlers;

import com.onextel.CallServiceApplication.freeswitch.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackEventHandler extends EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackEventHandler.class);

    @Override
    public void handleEvent(Event event) {
        LOGGER.info("Fallback - Processing event: {} eventSequence {}",
                event.getEventType(), event.getEventSequence());
        //LOGGER.info("Fallback Processing: {}", event.toString());
    }
}
