package com.project.goTruck.media.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the company-logo processing pipeline.
 *
 * Topology:
 *
 *   gotruck.logo (direct exchange)
 *     -- logo.processing.requested --> gotruck.logo.processing (queue)
 *                                          |
 *                                          | x-dead-letter-exchange:
 *                                          |   gotruck.logo.dlx
 *                                          | x-dead-letter-routing-key:
 *                                          |   logo.processing.dead
 *                                          v
 *   gotruck.logo.dlx (direct exchange)
 *     -- logo.processing.dead --> gotruck.logo.processing.dlq (queue)
 *
 * The main queue is declared with dead-letter arguments, so a message
 * rejected by the listener (after the in-memory retry policy from
 * spring.rabbitmq.listener.simple.retry is exhausted) is routed to the
 * DLQ instead of being lost or requeued forever.
 *
 * All declarations are idempotent: RabbitAdmin re-declares them on
 * every application startup, and existing matching declarations are
 * left untouched.
 */
@Configuration
public class RabbitLogoConfig {

    public static final String LOGO_EXCHANGE = "gotruck.logo";
    public static final String LOGO_PROCESSING_QUEUE = "gotruck.logo.processing";
    public static final String LOGO_PROCESSING_ROUTING_KEY = "logo.processing.requested";

    public static final String LOGO_DLX = "gotruck.logo.dlx";
    public static final String LOGO_PROCESSING_DLQ = "gotruck.logo.processing.dlq";
    public static final String LOGO_PROCESSING_DEAD_ROUTING_KEY = "logo.processing.dead";

    /**
     * JSON message converter so events travel as small JSON payloads
     * (e.g. LogoProcessingRequested) instead of Java serialization.
     */
    @Bean
    public MessageConverter logoMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange logoExchange() {
        return new DirectExchange(LOGO_EXCHANGE, true, false);
    }

    /**
     * Main processing queue. Durable, non-exclusive, with dead-letter
     * routing to the DLX when the listener rejects a message.
     */
    @Bean
    public Queue logoProcessingQueue() {
        return QueueBuilder.durable(LOGO_PROCESSING_QUEUE)
                .deadLetterExchange(LOGO_DLX)
                .deadLetterRoutingKey(LOGO_PROCESSING_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding logoProcessingBinding() {
        return BindingBuilder
                .bind(logoProcessingQueue())
                .to(logoExchange())
                .with(LOGO_PROCESSING_ROUTING_KEY);
    }

    @Bean
    public DirectExchange logoDeadLetterExchange() {
        return new DirectExchange(LOGO_DLX, true, false);
    }

    @Bean
    public Queue logoProcessingDeadLetterQueue() {
        return QueueBuilder.durable(LOGO_PROCESSING_DLQ).build();
    }

    @Bean
    public Binding logoDeadLetterBinding() {
        return BindingBuilder
                .bind(logoProcessingDeadLetterQueue())
                .to(logoDeadLetterExchange())
                .with(LOGO_PROCESSING_DEAD_ROUTING_KEY);
    }

    /**
     * Declares the whole logo topology eagerly at startup.
     *
     * Spring AMQP declares beans lazily on the first connection —
     * with no listener and no publisher yet, the queues/exchanges
     * would not exist until the first upload. Declaring them during
     * application startup makes the infrastructure state explicit
     * and verifiable right after boot, and surfaces RabbitMQ
     * connectivity problems immediately instead of at first use.
     *
     * Injects the AmqpAdmin interface — Spring Boot registers the
     * auto-configured admin under its interface type, so injection
     * by the concrete RabbitAdmin class finds no candidate bean.
     */
    @Bean
    public ApplicationRunner logoTopologyInitializer(AmqpAdmin amqpAdmin) {
        return (ApplicationArguments args) -> {
            amqpAdmin.declareExchange(logoExchange());
            amqpAdmin.declareQueue(logoProcessingQueue());
            amqpAdmin.declareBinding(logoProcessingBinding());
            amqpAdmin.declareExchange(logoDeadLetterExchange());
            amqpAdmin.declareQueue(logoProcessingDeadLetterQueue());
            amqpAdmin.declareBinding(logoDeadLetterBinding());
        };
    }
}
