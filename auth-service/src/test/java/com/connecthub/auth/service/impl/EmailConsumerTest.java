package com.connecthub.auth.service.impl;

import com.connecthub.auth.dto.event.EmailEvent;
import com.connecthub.auth.dto.event.EmailEvent.EmailType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailConsumer emailConsumer;

    @Test
    void handleEmailEvent_otpReset_doesNotThrow() {
        EmailEvent event = EmailEvent.builder()
                .type(EmailType.OTP_RESET)
                .toEmail("user@test.com")
                .otp("123456")
                .build();

        assertDoesNotThrow(() -> emailConsumer.handleEmailEvent(event));
    }

    @Test
    void handleEmailEvent_welcome_doesNotThrow() {
        EmailEvent event = EmailEvent.builder()
                .type(EmailType.WELCOME)
                .toEmail("welcome@test.com")
                .username("newuser")
                .build();

        assertDoesNotThrow(() -> emailConsumer.handleEmailEvent(event));
    }

    @Test
    void handleEmailEvent_nullType_doesNotThrow() {
        EmailEvent event = EmailEvent.builder()
                .toEmail("nulltype@test.com")
                .build();

        assertDoesNotThrow(() -> emailConsumer.handleEmailEvent(event));
    }
}
