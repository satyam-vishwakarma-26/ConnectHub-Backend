package com.connecthub.payment.service;

import com.connecthub.payment.config.RabbitMQConfig;
import com.connecthub.payment.dto.PaymentEmailEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEmailProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PaymentEmailProducer paymentEmailProducer;

    @Test
    void publishEmailEvent_Success() {
        PaymentEmailEvent event = PaymentEmailEvent.builder()
                .type("SUBSCRIPTION_ACTIVATED")
                .toEmail("test@example.com")
                .username("Test User")
                .planName("PRO")
                .amount(500.0)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1))
                .build();

        paymentEmailProducer.publishEmailEvent(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY,
                event
        );
    }
}
