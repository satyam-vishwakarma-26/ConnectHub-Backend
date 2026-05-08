package com.connecthub.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;

import java.io.InputStream;

/**
 * NoOpMailSender — a no-op JavaMailSender used in development
 * when app.notification.email-enabled=false.
 *
 * All send() calls are logged at WARN level instead of actually
 * sending email. This prevents startup failures when no SMTP
 * credentials are configured.
 */
@Slf4j
public class NoOpMailSender implements JavaMailSender {

    @Override
    public MimeMessage createMimeMessage() {
        try {
            return new MimeMessage((Session) null);
        } catch (Exception e) {
            throw new RuntimeException("NoOpMailSender: cannot create MimeMessage", e);
        }
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return createMimeMessage();
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        log.warn("[NoOpMailSender] Email send skipped — email is disabled in dev mode.");
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        log.warn("[NoOpMailSender] Bulk email send skipped — email is disabled in dev mode.");
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        log.warn("[NoOpMailSender] Email send skipped — email is disabled in dev mode.");
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        log.warn("[NoOpMailSender] Bulk email send skipped — email is disabled in dev mode.");
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        log.warn("[NoOpMailSender] SimpleMailMessage skipped — to={} subject={}",
                simpleMessage.getTo(), simpleMessage.getSubject());
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        log.warn("[NoOpMailSender] Bulk SimpleMailMessage skipped — {} messages",
                simpleMessages.length);
    }
}
