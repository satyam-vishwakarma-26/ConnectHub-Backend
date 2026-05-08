package com.connecthub.payment.service;

import com.connecthub.payment.config.RabbitMQConfig;
import com.connecthub.payment.dto.PaymentEmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes payment-related email events to the shared RabbitMQ email queue.
 * Auth-service's EmailConsumer picks them up and sends the SMTP email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEmailProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishEmailEvent(PaymentEmailEvent event) {
        log.info("Publishing payment email event: type={}, to={}", event.getType(), event.getToEmail());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY,
                event
        );
    }
}
