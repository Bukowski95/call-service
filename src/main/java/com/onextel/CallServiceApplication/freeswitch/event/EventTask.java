package com.onextel.CallServiceApplication.freeswitch.event;

import com.onextel.CallServiceApplication.freeswitch.event.handlers.EventHandler;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Class representing a task to process the event
 */
@Getter
public class EventTask implements Runnable, Comparable<EventTask> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventTask.class);

    private final Event event;
    private final int eventSequence;
    private final EventHandler eventHandler;
    private final Consumer<Boolean> ackCallback; // true=ack, false=nack

    public EventTask(
            Event event,
            EventHandler eventHandler) {
        this.event = event;
        this.eventHandler = eventHandler;
        this.eventSequence = event.getEventSequence();
        this.ackCallback = null;
    }

    public EventTask(Event event, EventHandler eventHandler, Consumer<Boolean> ackCallback) {
        this.event = event;
        this.eventHandler = eventHandler;
        this.eventSequence = event.getEventSequence();
        this.ackCallback = ackCallback;
    }

    @Override
    public int compareTo(EventTask other) {
        // Order events by Event-Sequence in ascending order
        return Integer.compare(this.eventSequence, other.eventSequence);
    }

    public void nack() {
        LOGGER.warn("Task rejected event: {} eventSequence {}", event.getEventType(), eventSequence);
        if (ackCallback != null) {
            ackCallback.accept(false); // Ack on success
        }
    }

    @Override
    public void run() {
        try {
            LOGGER.debug("Processing: {}", event.toString());
            // LOGGER.info("Processing event: {} eventSequence {}", event.getEventType(), eventSequence);
            eventHandler.handleEvent(event);
            if (ackCallback != null) {
                ackCallback.accept(true); // Ack on success
            }
        } catch (Exception exp) {
            LOGGER.error("Failed to handle event {}", event, exp);
            if (ackCallback != null) {
                ackCallback.accept(false); // Ack on success
            }
        }
    }
}