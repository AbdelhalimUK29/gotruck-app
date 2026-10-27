package com.project.goTruck.media.messaging;

import com.project.goTruck.media.config.RabbitLogoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Publishes logo-processing events to RabbitMQ.
 *
 * Uses the topology declared in {@link RabbitLogoConfig}: the
 * {@code gotruck.logo} direct exchange with routing key
 * {@code logo.processing.requested}, bound to the
 * {@code gotruck.logo.processing} queue (which dead-letters to the
 * DLQ after the listener retry policy is exhausted).
 *
 * The message is small JSON — never the image itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoProcessingPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publishes a processing request. Throws on broker failure so the
     * caller can compensate (the upload path cleans up the temp
     * object and returns an error rather than leaving an unresolvable
     * job behind).
     */
    public void publish(UUID uploadId, Long companyId, String tempObjectKey) {
        LogoProcessingRequested event = new LogoProcessingRequested(
                uploadId, companyId, tempObjectKey);

        rabbitTemplate.convertAndSend(
                RabbitLogoConfig.LOGO_EXCHANGE,
                RabbitLogoConfig.LOGO_PROCESSING_ROUTING_KEY,
                event);

        log.info("Published logo processing event: uploadId={}, companyId={}",
                uploadId, companyId);
    }
}
