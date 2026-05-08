package com.connecthub.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * MailConfig — creates a JavaMailSender bean ONLY when
 * app.notification.email-enabled=true is set in application.yml.
 *
 * In development (email-enabled=false), a no-op stub is provided so
 * that NotificationServiceImpl can still be wired without null injection.
 *
 * To enable real email:
 *   1. Set app.notification.email-enabled=true in application.yml
 *   2. Set app.mail.username and app.mail.password with real credentials
 *   3. For Gmail: create an App Password at myaccount.google.com/apppasswords
 */
@Configuration
@Slf4j
public class MailConfig {

    @Value("${app.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${app.mail.port:587}")
    private int port;

    @Value("${app.mail.username:}")
    private String username;

    @Value("${app.mail.password:}")
    private String password;

    /**
     * Real mail sender — only created when email-enabled=true.
     */
    @Bean
    @ConditionalOnProperty(name = "app.notification.email-enabled", havingValue = "true")
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");

        log.info("[Mail] JavaMailSender configured: host={} port={}", host, port);
        return mailSender;
    }

    /**
     * No-op stub — used in dev when email-enabled=false.
     * Prevents NullPointerException when NotificationServiceImpl
     * autowires JavaMailSender.
     */
    @Bean
    @ConditionalOnProperty(name = "app.notification.email-enabled",
                           havingValue = "false", matchIfMissing = true)
    public JavaMailSender noOpMailSender() {
        log.warn("[Mail] Email is DISABLED (app.notification.email-enabled=false). " +
                 "Emails will be logged but not sent.");
        return new NoOpMailSender();
    }
}
