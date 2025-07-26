package com.onextel.CallServiceApplication.service;

import com.onextel.CallServiceApplication.audit.AuditService;
import com.onextel.CallServiceApplication.freeswitch.command.CommandResponseCallback;
import com.onextel.CallServiceApplication.freeswitch.command.FreeSwitchCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommandService {

    private final static String DEFAULT_EXCHANGE_NAME = "TAP.Commands";
    private final static String FS_CORRELATION_ID_HEADER = "fs_correlation_id";
    private final RabbitTemplate rabbitTemplate;
    private final AuditService auditService;
    /**
     * In-memory map to track commands and their responses by correlationId
     */
    private final Map<String, FreeSwitchCommand> activeCommands = new ConcurrentHashMap<>();
    @Value("${app.rabbitmq.commands.exchange.name:" + DEFAULT_EXCHANGE_NAME + "}")
    private String commandsExchangeName;
    private String serviceInstanceId;
    private String replyToQueueName;


    /**
     * Send message synchronously to FreeSwitch commands queue.
     * FreeSwitch will get commands from queue and executes the command.
     * Once command is processed, FreeSwitch will add events in
     * events message queue.
     *
     * @param commandMessage The command to be processed on Freeswitch console
     * @param queueName      FreeSwitch commands queue with name
     *                       as {freeSwitch-domain-name}_command
     */
    public void sendMessage(String commandMessage, String queueName) {
        log.info("Sending command:{} queue:{}", queueName, commandMessage);
        Message message = MessageBuilder.withBody(commandMessage.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .build();
        rabbitTemplate.convertAndSend(commandsExchangeName, queueName, message);
        log.info("Message Sent to queue:{} message:{}", queueName, message);
    }

    /**
     * Send message to FreeSwitch commands queue with correlationId.
     * FreeSwitch will get commands from queue and adds it in its internal queue.
     * FreeSwitch will process messages from this queue asynchronously and add
     * command response to reply-queue with correlationId. It will also
     * add events in events message queue based on command.
     *
     * @param commandMessage The command to be processed on Freeswitch console
     * @param queueName      FreeSwitch commands queue with name
     *                       as {freeSwitch-domain-name}_command
     * @param correlationId  Identifier for the request to which later response will be
     *                       mapped.
     */
    public void sendMessageAsync(String commandMessage, String queueName,
                                 String correlationId, CommandResponseCallback callback) {
        log.debug("Sending command:queueName:[{}] replyQueue:[{}] correlationId:[{}] command:[{}]",
                queueName, replyToQueueName, correlationId, commandMessage);
        Message message = MessageBuilder.withBody(commandMessage.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                //.setReplyTo(replyToQueueName)
                .setReplyTo("freeswitchQueue") // TODO: get event queue name from function
                .setCorrelationId(correlationId)
                .setHeader(FS_CORRELATION_ID_HEADER, correlationId) // Additional header for FS
                .build();

        // Send message to FS commands queue
        rabbitTemplate.convertAndSend(commandsExchangeName, queueName, message);
        log.info("Message Sent to queue:{} correlationId:{} message:{}",
                queueName, correlationId, message);
        auditService.logCommandSent(correlationId, queueName, "freeswitchQueue", commandMessage);

        activeCommands.put(correlationId,
                new FreeSwitchCommand(correlationId, replyToQueueName,
                        commandMessage, callback));
    }

    private String getReplyQueueName(String serviceInstanceId) {
        return serviceInstanceId + "_reply_queue";
    }

    public void completeCommand(String correlationId, String responseBody) {
        try {
            // Match the response to the original command using the correlationId
            if (activeCommands.containsKey(correlationId)) {
                FreeSwitchCommand commandInfo = activeCommands.get(correlationId);
                log.info("Received response for CorrelationId:{},Command:{},Response:{}",
                        correlationId, commandInfo.getCommandMessage(), responseBody);
                commandInfo.setCommandResponse(responseBody);

                auditService.logCommandResponseReceived(correlationId,  responseBody);

                // Retrieve and call the callback function
                CommandResponseCallback callback = commandInfo.getCallback();
                if (callback != null) {
                    // Notify the callback
                    callback.onResponseReceived(correlationId, commandInfo);
                }
                // After processing, remove the command info from the map
                activeCommands.remove(correlationId);
            } else {
                log.warn("No command found for CorrelationId:{}", correlationId);
            }
        } catch (Exception e) {
            log.error("Failed to process command response for CorrelationId:{}",
                    correlationId, e);
        }
    }

    /**
     * Cleanup task, remove old commands that are still in map, means response is not
     * yet received.
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanupStaleCommands() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        activeCommands.entrySet().removeIf(entry ->
                entry.getValue().getCreatedAt().isBefore(cutoff));
    }

}


