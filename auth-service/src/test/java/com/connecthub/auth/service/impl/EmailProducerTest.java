package com.connecthub.auth.service.impl;

import com.connecthub.auth.config.RabbitMQConfig;
import com.connecthub.auth.dto.event.EmailEvent;
import com.connecthub.auth.dto.event.EmailEvent.EmailType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishEmailEvent_sendsToRabbitMQ() {
        EmailProducer emailProducer = new EmailProducer(rabbitTemplate);

        EmailEvent event = EmailEvent.builder()
                .type(EmailType.WELCOME)
                .toEmail("test@example.com")
                .username("testuser")
                .build();

        emailProducer.publishEmailEvent(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EMAIL_EXCHANGE),
                eq(RabbitMQConfig.EMAIL_ROUTING_KEY),
                eq(event));
    }

    @Test
    void publishEmailEvent_withOtp_sendsOtpEvent() {
        EmailProducer emailProducer = new EmailProducer(rabbitTemplate);

        EmailEvent event = EmailEvent.builder()
                .type(EmailType.OTP_RESET)
                .toEmail("user@example.com")
                .otp("123456")
                .build();

        emailProducer.publishEmailEvent(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EMAIL_EXCHANGE),
                eq(RabbitMQConfig.EMAIL_ROUTING_KEY),
                eq(event));
    }

    @Test
    void publishEmailEvent_subscriptionEvent_sendsSubscriptionEvent() {
        EmailProducer emailProducer = new EmailProducer(rabbitTemplate);

        EmailEvent event = EmailEvent.builder()
                .type(EmailType.SUBSCRIPTION_ACTIVATED)
                .toEmail("pro@example.com")
                .username("prouser")
                .amount(199.00)
                .currency("INR")
                .build();

        emailProducer.publishEmailEvent(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EMAIL_EXCHANGE),
                eq(RabbitMQConfig.EMAIL_ROUTING_KEY),
                eq(event));
    }
}