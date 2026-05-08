package com.connecthub.auth.service.impl;

import com.connecthub.auth.dto.event.EmailEvent;
import com.connecthub.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EmailService implementation that publishes email events to RabbitMQ.
 * The actual SMTP sending is handled by {@link EmailConsumer}.
 * 
 * Flow: Caller → EmailServiceImpl (producer) → RabbitMQ → EmailConsumer (SMTP sender)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final EmailProducer emailProducer;

    @Override
    public void sendRegistrationOtpEmail(String toEmail, String otp) {
        EmailEvent event = EmailEvent.builder()
                .type(EmailEvent.EmailType.REGISTRATION_OTP)
                .toEmail(toEmail)
                .otp(otp)
                .build();

        emailProducer.publishEmailEvent(event);
        log.info("Registration OTP email event queued for: {}", toEmail);
    }

    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        EmailEvent event = EmailEvent.builder()
                .type(EmailEvent.EmailType.OTP_RESET)
                .toEmail(toEmail)
                .otp(otp)
                .build();

        emailProducer.publishEmailEvent(event);
        log.info("OTP email event queued for: {}", toEmail);
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String username) {
        EmailEvent event = EmailEvent.builder()
                .type(EmailEvent.EmailType.WELCOME)
                .toEmail(toEmail)
                .username(username)
                .build();

        emailProducer.publishEmailEvent(event);
        log.info("Welcome email event queued for: {}", toEmail);
    }

    @Override
    public void sendAccountSuspendedEmail(String toEmail, String username) {
        EmailEvent event = EmailEvent.builder()
                .type(EmailEvent.EmailType.ACCOUNT_SUSPENDED)
                .toEmail(toEmail)
                .username(username)
                .build();

        emailProducer.publishEmailEvent(event);
        log.info("Account suspended email event queued for: {}", toEmail);
    }

    @Override
    public void sendAccountDeletedEmail(String toEmail, String username) {
        EmailEvent event = EmailEvent.builder()
                .type(EmailEvent.EmailType.ACCOUNT_DELETED)
                .toEmail(toEmail)
                .username(username)
                .build();

        emailProducer.publishEmailEvent(event);
        log.info("Account deleted email event queued for: {}", toEmail);
    }

    @Override
    public void sendAccountDeletionOtpEmail(String toEmail, String otp, String username) {
        EmailEvent event = EmailEvent.builder()
                .type(EmailEvent.EmailType.ACCOUNT_DELETION_OTP)
                .toEmail(toEmail)
                .otp(otp)
                .username(username)
                .build();

        emailProducer.publishEmailEvent(event);
        log.info("Account deletion OTP email event queued for: {}", toEmail);
    }

    @Override
    public void sendSelfDeletedAccountEmail(String toEmail, String username) {
        EmailEvent event = EmailEvent.builder()
                .type(EmailEvent.EmailType.ACCOUNT_SELF_DELETED)
                .toEmail(toEmail)
                .username(username)
                .build();

        emailProducer.publishEmailEvent(event);
        log.info("Self-deleted account farewell email event queued for: {}", toEmail);
    }
}
