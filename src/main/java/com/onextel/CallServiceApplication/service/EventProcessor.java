package com.onextel.CallServiceApplication.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onextel.CallServiceApplication.common.ThreadUtils;
import com.onextel.CallServiceApplication.freeswitch.event.*;
import com.onextel.CallServiceApplication.freeswitch.event.handlers.EventHandler;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventProcessor {
    /**
     * Limit number of concurrent event processing threads
     */
    private static final int MAX_EVENT_PROCESSOR_THREADS = 50;

    /**
     * Limit size of channel queue to prevent overflow
     */
    private static final int CHANNEL_QUEUE_CAPACITY = 100;

    // Executor service for processing events, general pool for tasks
    private final ExecutorService eventExecutorService = Executors.newFixedThreadPool(MAX_EVENT_PROCESSOR_THREADS);
    private final ObjectMapper objectMapper;

    /**
     * Maintain event queue per channel so channel events are processed
     * based on sequence number.
     */
    private final ConcurrentHashMap<String, PriorityBlockingQueue<EventTask>> channelEventQueues = new ConcurrentHashMap<>();
    private final EventHandlerFactory eventHandlerFactory;

    private List<MessageListenerContainer> listenerContainers; // used in shutdown
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final RabbitAdmin rabbitAdmin;
    private String queueName = "freeswitchQueue";

    @PostConstruct
    public void setup() {
        ((ThreadPoolExecutor) eventExecutorService).setRejectedExecutionHandler((r, executor) -> {
            if (shuttingDown.get()) {
                log.warn("Rejected task during shutdown: {}", r);
            } else {
                log.error("Task rejected while executor is active. Consider increasing pool or queue size.");
            }
        });
    }

    // Graceful shutdown of all executors
    public void shutdown() {
        try {
            // Mark that we're shutting down
            shuttingDown.set(true);
            log.info("Starting graceful shutdown...");

            // Stop all RabbitMQ listener containers to prevent more events from coming in
            if (listenerContainers != null) {
                for (MessageListenerContainer container : listenerContainers) {
                    container.stop(() -> {
                        // This callback is called when the container is actually stopped
                        log.debug("Listener container {} stopped", container);
                    });
                }
                log.info("RabbitMQ listeners stopping asynchronously...");
            }

            // Wait a brief moment to allow in-flight messages to be processed/rejected
            ThreadUtils.safeSleep(500, "EventProcessor shutdown waiting to process in-flight messages");

            // Process remaining messages in queues
            drainChannelQueues(Duration.ofSeconds(20));

            // Shutdown executor
            eventExecutorService.shutdown();
            if (!eventExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Event Executor did not terminate in time, forcing shutdown...");
                eventExecutorService.shutdownNow();
            }

            log.info("EventProcessor shutdown complete");

        } catch (InterruptedException e) {
            log.error("Interrupted during EventProcessor shutdown ", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error during EventProcessor shutdown", e);
        }
    }

//    @RabbitListener(queues = "freeswitchQueue") // Auto ACK
//    public void processEvent(Message message) {
//        try {
//            String eventPayload = new String(message.getBody());
//            //log.info("Event Message : {}", eventPayload);
//            Event event = parseEvent(eventPayload);
//            if (event == null) {
//                return;
//            }
//            EventType eventType = event.getEventType();
//            EventHandler handler = eventHandlerFactory.getEventHandler(eventType);
//            if (eventType == EventType.HEARTBEAT) {
//                handler.handleEvent(event);
//                return;
//            }
//            EventTask eventTask = new EventTask(event, handler);
//            if (EventUtils.isChannelEvent(eventType)) {
//                String channelId = event.getStringParam(EventParams.CHANNEL_CALL_UUID);
//                handleChannelEvent(channelId, eventTask);
//            } else {
//                handleGeneralEvent(eventTask);
//            }
//        } catch (Exception exp) {
//            String errorMessage = String.format(
//                    "Failed to process message %s", message);
//            log.error(errorMessage, exp);
//        }
//    }

    @RabbitListener(queues = "freeswitchQueue", ackMode = "MANUAL") //Manual ack
    public void processEvent(Message message, Channel channel) {
        if (shuttingDown.get()) {
            rejectMessage(message, channel);
            return;
        }

        try {
            String eventPayload = new String(message.getBody());
            log.info("Event Message : {}", eventPayload);
            Event event = parseEvent(eventPayload);
            if (event == null) {
                rejectMessage(message, channel);
                return;
            }

            EventType eventType = event.getEventType();
            EventHandler handler = eventHandlerFactory.getEventHandler(eventType);

            if (eventType == EventType.HEARTBEAT) {
                handler.handleEvent(event);
                ackMessage(message, channel);
                return;
            }

            // Create task with ack/nack callback
            EventTask eventTask = new EventTask(event, handler, (ack) -> {
                if (channel == null || !channel.isOpen()) {
                    log.warn("Channel is not available or closed, cannot ack/nack message");
                    return;
                }
                try {
                    if (ack) {
                        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                    } else {
                        channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, !shuttingDown.get());
                    }
                } catch (Exception e) {
                    log.error("Failed to ack/nack message", e);
                }
            });
            if (EventUtils.isChannelEvent(eventType)) {
                handleChannelEvent(event.getStringParam(EventParams.CHANNEL_CALL_UUID), eventTask);
            } else {
                handleGeneralEvent(eventTask);
            }

        } catch (Exception exp) {
            log.error("Failed to process message {}", message, exp);
            rejectMessage(message, channel);
        }
    }

    private Event parseEvent(String eventPayload) {
        try {
            Map<String, Object> eventDetails = objectMapper.readValue(eventPayload,
                    new TypeReference<Map<String, Object>>() {
                    });
            return new Event(eventDetails);
        } catch (JsonProcessingException e) {
            String errorMessage = String.format(
                    "JsonProcessingException -> Failed to process message %s", eventPayload);
            log.error(errorMessage, e);
            return null;
        } catch (IllegalArgumentException e) {
            String errorMessage = String.format(
                    "Failed to create Event object from message %s", eventPayload);
            log.error(errorMessage, e);
            return null;
        }
    }

    private void handleChannelEvent(String channelId, EventTask eventTask) {
        try {
            if (shuttingDown.get()) {
                eventTask.nack(); // Reject immediately if shutting down
                return;
            }

            // Ensure there's a priority queue for the channel
            PriorityBlockingQueue<EventTask> channelQueue =
                    channelEventQueues.computeIfAbsent(
                            channelId,
                            k -> new PriorityBlockingQueue<>(CHANNEL_QUEUE_CAPACITY)
                    );
            // Add the event to the queue
            if (!channelQueue.offer(eventTask)) {
                log.warn("Channel queue full for channel {}, rejecting message", channelId);
                eventTask.nack();
                return;
            }

            // Process the channel events sequentially by sequence number
            eventExecutorService.submit(() -> processChannelEvent(channelId, channelQueue));

        }  catch (RejectedExecutionException ex) {
            log.warn("Rejected event task during shutdown");
            eventTask.nack();
        }
    }

    void handleGeneralEvent(EventTask eventTask) {
        try {
            if (shuttingDown.get()) {
                eventTask.nack(); // Reject immediately if shutting down
                return;
            }
            eventExecutorService.submit(eventTask);
        } catch (RejectedExecutionException ex) {
            log.warn("Rejected general task during shutdown");
            eventTask.nack();
        }
    }

    private void processChannelEvent(String channelId, PriorityBlockingQueue<EventTask> channelQueue) {
        try {
            while (!Thread.currentThread().isInterrupted() && !shuttingDown.get()) {
                EventTask eventTask = channelQueue.poll(100, TimeUnit.MILLISECONDS);
                if (eventTask != null) {
                    eventTask.run();
                } else if (channelQueue.isEmpty()) {
                    // Clean up empty queues
                    channelEventQueues.remove(channelId);
                    break;
                }
            }

            // Special handling during shutdown - process any remaining tasks if we're shutting down
            if (shuttingDown.get()) {
                EventTask remainingTask;
                while ((remainingTask = channelQueue.poll()) != null) {
                    try {
                        remainingTask.run();
                    } catch (Exception e) {
                        log.warn("Failed to process task during shutdown", e);
                        remainingTask.nack();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Event processing interrupted for channel {}", channelId, e);
        }
    }

    private boolean validateChannel(Channel channel) {
        return channel != null && channel.isOpen();
    }

    private void ackMessage(Message message, Channel channel) {
        try {
            if (channel == null || !channel.isOpen()) {
                log.warn("Channel is not available or closed, cannot ack message");
                return;
            }

            long deliveryTag = message.getMessageProperties().getDeliveryTag();
            channel.basicAck(deliveryTag, false);

            if (log.isTraceEnabled()) {
                log.trace("Acked message with delivery tag: {}", message);
            }

        } catch (IOException e) {
            log.error("Failed to ack message with delivery tag: {}",
                    message.getMessageProperties().getDeliveryTag(), e);
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed while trying to ack message");
        } catch (Exception e) {
            log.error("Unexpected error while sending ack message", e);
        }
    }

    private void rejectMessage(Message message, Channel channel) {
        try {
            if (channel == null || !channel.isOpen()) {
                return;
            }

            // Always requeue if we're shutting down
            boolean requeue = shuttingDown.get();
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), requeue);

            if (log.isTraceEnabled()) {
                log.trace("Re-queued message during shutdown: {}", message);
            }
        } catch (IOException ioEx) {
            log.error("Failed to reject message", ioEx);
        }
    }

    private void drainChannelQueues(Duration timeout) {
        long start = System.currentTimeMillis();
        long maxTime = timeout.toMillis();

        while (System.currentTimeMillis() - start < maxTime) {
            boolean allQueuesEmpty = true;

            for (Map.Entry<String, PriorityBlockingQueue<EventTask>> entry : channelEventQueues.entrySet()) {
                PriorityBlockingQueue<EventTask> queue = entry.getValue();
                EventTask task;
                while ((task = queue.poll()) != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.warn("Error running remaining task on shutdown", e);
                    }
                }
                if (!queue.isEmpty()) {
                    allQueuesEmpty = false;
                }
            }

            if (allQueuesEmpty) {
                log.info("All channel queues drained.");
                return;
            }

            if (ThreadUtils.safeSleep(100, "event processor - drainChannelQueues")) {
                break;
            }
        }

        log.warn("Timeout reached while draining queues. Some events may remain.");
    }

    @Scheduled(fixedDelay = 60000)
    public void basicMonitor() {
        QueueInformation queueInfo = rabbitAdmin.getQueueInfo(queueName);
        if (queueInfo != null) {
            log.info("Queue {} - Messages: {}, Consumers: {}",
                    queueName,
                    queueInfo.getMessageCount(),
                    queueInfo.getConsumerCount());
        }
    }

}
