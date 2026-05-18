package com.connecthub.auth.service.impl;

import com.connecthub.auth.config.RabbitMQConfig;
import com.connecthub.auth.dto.event.EmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ producer — publishes email events to the message queue.
 * Decouples email sending from the business logic layer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish an email event to the RabbitMQ exchange.
     * The consumer will pick it up and send the actual SMTP email.
     */
    public void publishEmailEvent(EmailEvent event) {
        log.info("Publishing email event: type={}, to={}", event.getType(), event.getToEmail());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EMAIL_EXCHANGE,
                    RabbitMQConfig.EMAIL_ROUTING_KEY,
                    event
            );
            log.debug("Successfully published email event to RabbitMQ");
        } catch (Exception e) {
            log.error("Failed to publish email event to RabbitMQ for {}: {}", event.getToEmail(), e.getMessage());
            // We don't rethrow because we don't want to break the main request
            // in case of transient RabbitMQ issues. 
            // The OTP will still be in the DB if the user tries to verify later.
        }
    }
}
