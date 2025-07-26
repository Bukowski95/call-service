package com.onextel.CallServiceApplication.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.FatalExceptionStrategy;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRabbit
@Slf4j
public class RabbitConfig {

    // Queue for receiving events from FreeSwitch - TAP.Events
    public static final String EVENT_EXCHANGE_NAME = "TAP.Events";
    public static final String EVENT_QUEUE_NAME = "freeswitchQueue";
    public static final String EVENT_ROUTING_KEY = "#"; // Wildcard for all events

    // Queue and exchange for sending commands to FreeSwitch TAP.Commands
    public static final String COMMAND_EXCHANGE_NAME = "TAP.Commands";
    public static final String COMMAND_QUEUE_NAME = "OL03LTW-GNR0104_command"; // Existing command queue
    public static final String COMMAND_ROUTING_KEY = "OL03LTW-GNR0104_command"; // Use specific routing key if needed

    public static final String MESSAGE_HEADER_TTL = "x-message-ttl";
    public static final int MESSAGE_TTL_MS = 60000; // 60 seconds
    public static final String MESSAGE_HEADER_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "dlx_exchange";

    @Value("${spring.rabbitmq.host}")
    private String rabbitMqHost;

    @Value("${spring.rabbitmq.port}")
    private int rabbitMqPort;

    @Value("${spring.rabbitmq.username}")
    private String rabbitMqUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitMqPassword;

    @Value("${spring.rabbitmq.virtual-host}")
    private String rabbitMqVirtualHost;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitMqHost);
        connectionFactory.setPort(rabbitMqPort);
        connectionFactory.setUsername(rabbitMqUsername);
        connectionFactory.setPassword(rabbitMqPassword);
        connectionFactory.setVirtualHost(rabbitMqVirtualHost);

        connectionFactory.setRequestedHeartBeat(30); // 30 seconds heartbeat
        connectionFactory.setConnectionTimeout(10000); // 10 seconds connection timeout
        return connectionFactory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // Enable manual ack
        factory.setPrefetchCount(50); // Control how many messages are prefetched
        factory.setDefaultRequeueRejected(false); // Don't automatically requeue on failure

        // Additional settings
        factory.setConcurrentConsumers(5); // Initial number of consumers based on thread pool size;
        factory.setMaxConcurrentConsumers(10); // Maximum number of consumers

        factory.setMissingQueuesFatal(false); // Don't fail if queues are missing at startup
        factory.setAutoStartup(true); // Start the container automatically

        // Error handling
        factory.setErrorHandler(new ConditionalRejectingErrorHandler(
                new FatalExceptionStrategy() {
                    @Override
                    public boolean isFatal(Throwable throwable) {
                        // Consider these exceptions as fatal (won't be retried)
                        if (throwable instanceof MessageConversionException ||
                                throwable instanceof org.springframework.amqp.AmqpIllegalStateException) {
                            return true;
                        }

                        // For IllegalStateException, be more selective
                        if (throwable instanceof IllegalStateException) {
                            String message = throwable.getMessage();
                            // Consider these common cases as fatal
                            return message != null && (
                                    message.contains("shutdown") ||
                                            message.contains("not active") ||
                                            message.contains("incompatible"));
                        }

                        return false;
                    }
                }
        ));

        return factory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public RabbitListenerEndpointRegistry endpointRegistry() {
        return new RabbitListenerEndpointRegistry();
    }

    //    @Bean
//    @Primary
//    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
//        return new RabbitTemplate(connectionFactory);
//    }

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        // Enable returns callback
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> {
            String messageBody = returned.getMessage() != null ?
                    new String(returned.getMessage().getBody()) : "null";

            log.warn("Message returned - Exchange: {}, RoutingKey: {}, ReplyCode: {}, ReplyText: {}, Body: {}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    messageBody);
        });

        return rabbitTemplate;
    }

    // Queue for receiving events
    @Bean
    public Queue eventQueue() {
        return new Queue(EVENT_QUEUE_NAME, false);
    }

    // Exchange for events
    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EVENT_EXCHANGE_NAME);
    }

    // Binding between the event queue and event exchange (wildcard for all events)
    @Bean
    public Binding eventBinding(Queue eventQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(eventQueue).to(eventExchange).with(EVENT_ROUTING_KEY);
    }

    // Queue for sending commands
    @Bean
    public Queue commandQueue() {
        Map<String, Object> args = new HashMap<>();
       // TODO: Configure these at RabbitMQ server side
        // while installing RabbitMQ Server
        // args.put(MESSAGE_HEADER_TTL, MESSAGE_TTL_MS);
        //args.put(MESSAGE_HEADER_DEAD_LETTER_EXCHANGE, DEAD_LETTER_EXCHANGE_NAME);
        return new Queue(COMMAND_QUEUE_NAME, false, false, true, args);
    }

    // Exchange for sending commands
    @Bean
    public TopicExchange commandExchange() {
        return new TopicExchange(COMMAND_EXCHANGE_NAME);
    }

    // Binding between the command queue and command exchange (with specified routing key)
    @Bean
    public Binding commandBinding(Queue commandQueue, TopicExchange commandExchange) {
        return BindingBuilder.bind(commandQueue).to(commandExchange).with(COMMAND_ROUTING_KEY);
    }
}
