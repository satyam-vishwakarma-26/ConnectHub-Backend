package com.connecthub.auth.service.impl;

import com.connecthub.auth.config.RabbitMQConfig;
import com.connecthub.auth.dto.event.EmailEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer — listens on the email queue and sends actual SMTP emails.
 * This is the ONLY place where JavaMailSender is used.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final String FROM_NAME = "ConnectHub";

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handleEmailEvent(EmailEvent event) {
        log.info("Consumed email event: type={}, to={}", event.getType(), event.getToEmail());

        try {
            switch (event.getType()) {
                case OTP_RESET              -> sendOtpResetEmail(event);
                case REGISTRATION_OTP       -> sendRegistrationOtpEmail(event);
                case WELCOME               -> sendWelcomeEmail(event);
                case ACCOUNT_SUSPENDED     -> sendAccountSuspendedEmail(event);
                case ACCOUNT_DELETED        -> sendAccountDeletedEmail(event);
                case ACCOUNT_DELETION_OTP   -> sendAccountDeletionOtpEmail(event);
                case ACCOUNT_SELF_DELETED   -> sendSelfDeletedAccountEmail(event);
                case SUBSCRIPTION_ACTIVATED -> sendSubscriptionActivatedEmail(event);
                case SUBSCRIPTION_CANCELLED -> sendSubscriptionCancelledEmail(event);
                default -> log.warn("Unknown email type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process email event for {}: {}", event.getToEmail(), e.getMessage(), e);
            // In production, you'd push to a dead-letter queue for retry
        }
    }

    // ── OTP Reset Email ───────────────────────────────────

    private void sendOtpResetEmail(EmailEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("Password Reset OTP — ConnectHub");
        helper.setText(buildOtpEmailBody(event.getOtp()), true);

        mailSender.send(message);
        log.info("OTP email sent to: {}", event.getToEmail());
    }

    // ── Welcome Email ─────────────────────────────────────

    private void sendWelcomeEmail(EmailEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("Welcome to ConnectHub! 🚀");
        helper.setText(buildWelcomeEmailBody(event.getUsername()), true);

        mailSender.send(message);
        log.info("Welcome email sent to: {}", event.getToEmail());
    }

    // ── HTML Templates ────────────────────────────────────

    private String buildOtpEmailBody(String otp) {
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto;
                        background: #0f0f1a; border-radius: 16px; overflow: hidden;">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #6366f1, #a78bfa, #c084fc); padding: 32px 24px; text-align: center;">
                    <h1 style="color: #ffffff; font-size: 28px; margin: 0; font-weight: 800; letter-spacing: -0.5px;">
                        ConnectHub
                    </h1>
                    <p style="color: rgba(255,255,255,0.85); font-size: 13px; margin: 6px 0 0;">
                        Secure Password Reset
                    </p>
                </div>
                <!-- Body -->
                <div style="padding: 32px 28px;">
                    <p style="color: #d1d5db; font-size: 15px; margin: 0 0 24px; line-height: 1.6;">
                        We received a request to reset your password. Use the verification code below to continue:
                    </p>
                    <div style="text-align: center; margin: 28px 0;">
                        <div style="display: inline-block; background: linear-gradient(135deg, #1e1b4b, #312e81);
                                    padding: 20px 40px; border-radius: 14px;
                                    border: 1px solid rgba(139,92,246,0.3);">
                            <span style="font-size: 40px; font-weight: 800; letter-spacing: 12px;
                                         background: linear-gradient(135deg, #a78bfa, #c084fc);
                                         -webkit-background-clip: text; -webkit-text-fill-color: transparent;
                                         font-family: 'Courier New', monospace;">
                                %s
                            </span>
                        </div>
                    </div>
                    <div style="background: rgba(245,158,11,0.08); border-left: 3px solid #f59e0b;
                                padding: 12px 16px; border-radius: 0 8px 8px 0; margin: 24px 0;">
                        <p style="color: #fbbf24; font-size: 13px; margin: 0; font-weight: 600;">
                            ⏱ This code expires in 5 minutes
                        </p>
                    </div>
                    <p style="color: #6b7280; font-size: 12px; margin: 24px 0 0; line-height: 1.6;">
                        If you didn't request this, you can safely ignore this email.
                        Never share this code with anyone — ConnectHub will never ask for it.
                    </p>
                </div>
                <!-- Footer -->
                <div style="padding: 16px 28px; border-top: 1px solid rgba(255,255,255,0.06); text-align: center;">
                    <p style="color: #4b5563; font-size: 11px; margin: 0;">
                        © 2026 ConnectHub · Secure real-time messaging
                    </p>
                </div>
            </div>
            """.formatted(otp);
    }

    private String buildWelcomeEmailBody(String username) {
        String displayName = (username != null && !username.isBlank()) ? username : "there";
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto;
                        background: #0f0f1a; border-radius: 16px; overflow: hidden;">
                <!-- Header with gradient -->
                <div style="background: linear-gradient(135deg, #6366f1, #8b5cf6, #a78bfa); padding: 40px 24px; text-align: center;">
                    <div style="display: inline-block; background: rgba(255,255,255,0.15);
                                padding: 12px 16px; border-radius: 14px; margin-bottom: 16px;">
                        <span style="font-size: 36px;">🚀</span>
                    </div>
                    <h1 style="color: #ffffff; font-size: 30px; margin: 0; font-weight: 800; letter-spacing: -0.5px;">
                        Welcome to ConnectHub!
                    </h1>
                    <p style="color: rgba(255,255,255,0.82); font-size: 15px; margin: 8px 0 0;">
                        Your communication, supercharged.
                    </p>
                </div>
                <!-- Body -->
                <div style="padding: 32px 28px;">
                    <p style="color: #e5e7eb; font-size: 16px; margin: 0 0 8px; font-weight: 600;">
                        Hey %s! 👋
                    </p>
                    <p style="color: #9ca3af; font-size: 14px; margin: 0 0 28px; line-height: 1.7;">
                        We're thrilled to have you on board. ConnectHub is built for teams and individuals
                        who want fast, secure, real-time conversations that just work.
                    </p>
                    <!-- Feature cards -->
                    <div style="margin: 0 0 28px;">
                        <div style="display: flex; gap: 12px; margin-bottom: 12px;">
                            <div style="flex: 1; background: linear-gradient(135deg, #1e1b4b, #1e1b4b);
                                        border: 1px solid rgba(99,102,241,0.2); border-radius: 12px; padding: 16px;">
                                <div style="font-size: 22px; margin-bottom: 8px;">💬</div>
                                <p style="color: #c4b5fd; font-size: 13px; font-weight: 600; margin: 0 0 4px;">Real-time Chat</p>
                                <p style="color: #6b7280; font-size: 11px; margin: 0;">Instant messages with typing indicators</p>
                            </div>
                            <div style="flex: 1; background: linear-gradient(135deg, #1e1b4b, #1e1b4b);
                                        border: 1px solid rgba(99,102,241,0.2); border-radius: 12px; padding: 16px;">
                                <div style="font-size: 22px; margin-bottom: 8px;">🔒</div>
                                <p style="color: #c4b5fd; font-size: 13px; font-weight: 600; margin: 0 0 4px;">Private Rooms</p>
                                <p style="color: #6b7280; font-size: 11px; margin: 0;">Secure spaces for your team</p>
                            </div>
                        </div>
                        <div style="display: flex; gap: 12px;">
                            <div style="flex: 1; background: linear-gradient(135deg, #1e1b4b, #1e1b4b);
                                        border: 1px solid rgba(99,102,241,0.2); border-radius: 12px; padding: 16px;">
                                <div style="font-size: 22px; margin-bottom: 8px;">🌐</div>
                                <p style="color: #c4b5fd; font-size: 13px; font-weight: 600; margin: 0 0 4px;">Presence</p>
                                <p style="color: #6b7280; font-size: 11px; margin: 0;">See who's online in real-time</p>
                            </div>
                            <div style="flex: 1; background: linear-gradient(135deg, #1e1b4b, #1e1b4b);
                                        border: 1px solid rgba(99,102,241,0.2); border-radius: 12px; padding: 16px;">
                                <div style="font-size: 22px; margin-bottom: 8px;">⚡</div>
                                <p style="color: #c4b5fd; font-size: 13px; font-weight: 600; margin: 0 0 4px;">Blazing Fast</p>
                                <p style="color: #6b7280; font-size: 11px; margin: 0;">WebSocket-powered messaging</p>
                            </div>
                        </div>
                    </div>
                    <!-- CTA Button -->
                    <div style="text-align: center; margin: 32px 0 24px;">
                        <a href="http://localhost:3000/login"
                           style="display: inline-block; background: linear-gradient(135deg, #6366f1, #8b5cf6);
                                  color: #ffffff; text-decoration: none; font-size: 15px; font-weight: 700;
                                  padding: 14px 40px; border-radius: 12px; letter-spacing: 0.3px;
                                  box-shadow: 0 4px 20px rgba(99,102,241,0.4);">
                            Start Chatting →
                        </a>
                    </div>
                    <p style="color: #4b5563; font-size: 12px; text-align: center; margin: 0; line-height: 1.6;">
                        Questions? Just reply to this email — we'd love to help.
                    </p>
                </div>
                <!-- Footer -->
                <div style="padding: 16px 28px; border-top: 1px solid rgba(255,255,255,0.06); text-align: center;">
                    <p style="color: #4b5563; font-size: 11px; margin: 0;">
                        © 2026 ConnectHub · Real-time messaging for modern teams
                    </p>
                </div>
            </div>
            """.formatted(displayName);
    }

    // ── Account Suspended Email ────────────────────────────

    private void sendAccountSuspendedEmail(EmailEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("Account Suspended — ConnectHub");
        helper.setText(buildSuspendedEmailBody(event.getUsername()), true);

        mailSender.send(message);
        log.info("Account suspended email sent to: {}", event.getToEmail());
    }

    // ── Account Deleted Email ──────────────────────────────

    private void sendAccountDeletedEmail(EmailEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("Account Deleted — ConnectHub");
        helper.setText(buildDeletedEmailBody(event.getUsername()), true);

        mailSender.send(message);
        log.info("Account deleted email sent to: {}", event.getToEmail());
    }

    // ── Suspended Email Template ───────────────────────────

    private String buildSuspendedEmailBody(String username) {
        String displayName = (username != null && !username.isBlank()) ? username : "User";
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto;
                        background: #0f0f1a; border-radius: 16px; overflow: hidden;">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #dc2626, #ef4444, #f87171); padding: 32px 24px; text-align: center;">
                    <div style="display: inline-block; background: rgba(255,255,255,0.15);
                                padding: 10px 14px; border-radius: 14px; margin-bottom: 14px;">
                        <span style="font-size: 32px;">⚠️</span>
                    </div>
                    <h1 style="color: #ffffff; font-size: 26px; margin: 0; font-weight: 800; letter-spacing: -0.5px;">
                        Account Suspended
                    </h1>
                    <p style="color: rgba(255,255,255,0.82); font-size: 13px; margin: 6px 0 0;">
                        ConnectHub — Important Notice
                    </p>
                </div>
                <!-- Body -->
                <div style="padding: 32px 28px;">
                    <p style="color: #e5e7eb; font-size: 16px; margin: 0 0 8px; font-weight: 600;">
                        Hi %s,
                    </p>
                    <p style="color: #9ca3af; font-size: 14px; margin: 0 0 24px; line-height: 1.7;">
                        Your ConnectHub account has been <strong style="color: #fca5a5;">suspended</strong>
                        by a platform administrator due to a policy violation.
                    </p>
                    <div style="background: rgba(239,68,68,0.08); border-left: 3px solid #ef4444;
                                padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 0 0 24px;">
                        <p style="color: #fca5a5; font-size: 13px; margin: 0; font-weight: 600;">
                            🚫 What this means:
                        </p>
                        <ul style="color: #9ca3af; font-size: 13px; margin: 8px 0 0; padding-left: 18px; line-height: 1.8;">
                            <li>You cannot log in to your account</li>
                            <li>Your messages and data are preserved</li>
                            <li>Your account may be reactivated upon review</li>
                        </ul>
                    </div>
                    <p style="color: #6b7280; font-size: 12px; margin: 0; line-height: 1.6;">
                        If you believe this is an error, please contact our support team
                        by replying to this email.
                    </p>
                </div>
                <!-- Footer -->
                <div style="padding: 16px 28px; border-top: 1px solid rgba(255,255,255,0.06); text-align: center;">
                    <p style="color: #4b5563; font-size: 11px; margin: 0;">
                        © 2026 ConnectHub · Platform Administration
                    </p>
                </div>
            </div>
            """.formatted(displayName);
    }

    // ── Deleted Email Template ─────────────────────────────

    private String buildDeletedEmailBody(String username) {
        String displayName = (username != null && !username.isBlank()) ? username : "User";
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto;
                        background: #0f0f1a; border-radius: 16px; overflow: hidden;">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #7f1d1d, #991b1b, #b91c1c); padding: 32px 24px; text-align: center;">
                    <div style="display: inline-block; background: rgba(255,255,255,0.15);
                                padding: 10px 14px; border-radius: 14px; margin-bottom: 14px;">
                        <span style="font-size: 32px;">🗑️</span>
                    </div>
                    <h1 style="color: #ffffff; font-size: 26px; margin: 0; font-weight: 800; letter-spacing: -0.5px;">
                        Account Permanently Deleted
                    </h1>
                    <p style="color: rgba(255,255,255,0.82); font-size: 13px; margin: 6px 0 0;">
                        ConnectHub — Final Notice
                    </p>
                </div>
                <!-- Body -->
                <div style="padding: 32px 28px;">
                    <p style="color: #e5e7eb; font-size: 16px; margin: 0 0 8px; font-weight: 600;">
                        Hi %s,
                    </p>
                    <p style="color: #9ca3af; font-size: 14px; margin: 0 0 24px; line-height: 1.7;">
                        We're writing to inform you that your ConnectHub account has been
                        <strong style="color: #fca5a5;">permanently deleted</strong>
                        by a platform administrator.
                    </p>
                    <div style="background: rgba(127,29,29,0.15); border-left: 3px solid #991b1b;
                                padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 0 0 24px;">
                        <p style="color: #fca5a5; font-size: 13px; margin: 0; font-weight: 600;">
                            ❌ This action is irreversible:
                        </p>
                        <ul style="color: #9ca3af; font-size: 13px; margin: 8px 0 0; padding-left: 18px; line-height: 1.8;">
                            <li>Your account and profile have been removed</li>
                            <li>All associated data has been deleted</li>
                            <li>You will no longer be able to log in</li>
                        </ul>
                    </div>
                    <p style="color: #9ca3af; font-size: 13px; margin: 0 0 20px; line-height: 1.6;">
                        You are welcome to create a new account at any time by visiting
                        <a href="http://localhost:3000/register" style="color: #818cf8; text-decoration: none; font-weight: 600;">ConnectHub</a>.
                    </p>
                    <p style="color: #6b7280; font-size: 12px; margin: 0; line-height: 1.6;">
                        If you believe this was done in error, please contact support
                        by replying to this email.
                    </p>
                </div>
                <!-- Footer -->
                <div style="padding: 16px 28px; border-top: 1px solid rgba(255,255,255,0.06); text-align: center;">
                    <p style="color: #4b5563; font-size: 11px; margin: 0;">
                        © 2026 ConnectHub · Platform Administration
                    </p>
                </div>
            </div>
            """.formatted(displayName);
    }

    // ── Account Deletion OTP Email ─────────────────────────

    private void sendAccountDeletionOtpEmail(EmailEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("⚠️ Account Deletion Verification — ConnectHub");
        helper.setText(buildAccountDeletionOtpBody(event.getOtp(), event.getUsername()), true);

        mailSender.send(message);
        log.info("Account deletion OTP email sent to: {}", event.getToEmail());
    }

    // ── Self-Deleted Account Farewell Email ────────────────

    private void sendSelfDeletedAccountEmail(EmailEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("Your ConnectHub Account Has Been Deleted");
        helper.setText(buildSelfDeletedAccountBody(event.getUsername()), true);

        mailSender.send(message);
        log.info("Self-deleted account farewell email sent to: {}", event.getToEmail());
    }

    // ── Account Deletion OTP Template ──────────────────────

    private String buildAccountDeletionOtpBody(String otp, String username) {
        String displayName = (username != null && !username.isBlank()) ? username : "there";
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto;
                        background: #0f0f1a; border-radius: 16px; overflow: hidden;">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #dc2626, #b91c1c, #991b1b); padding: 32px 24px; text-align: center;">
                    <div style="display: inline-block; background: rgba(255,255,255,0.15);
                                padding: 10px 14px; border-radius: 14px; margin-bottom: 14px;">
                        <span style="font-size: 32px;">🛡️</span>
                    </div>
                    <h1 style="color: #ffffff; font-size: 26px; margin: 0; font-weight: 800; letter-spacing: -0.5px;">
                        Account Deletion Request
                    </h1>
                    <p style="color: rgba(255,255,255,0.82); font-size: 13px; margin: 6px 0 0;">
                        ConnectHub — Security Verification
                    </p>
                </div>
                <!-- Body -->
                <div style="padding: 32px 28px;">
                    <p style="color: #e5e7eb; font-size: 16px; margin: 0 0 8px; font-weight: 600;">
                        Hi %s,
                    </p>
                    <p style="color: #9ca3af; font-size: 14px; margin: 0 0 24px; line-height: 1.7;">
                        We received a request to <strong style="color: #fca5a5;">permanently delete</strong>
                        your ConnectHub account. Use the verification code below to proceed:
                    </p>
                    <div style="text-align: center; margin: 28px 0;">
                        <div style="display: inline-block; background: linear-gradient(135deg, #450a0a, #7f1d1d);
                                    padding: 20px 40px; border-radius: 14px;
                                    border: 1px solid rgba(239,68,68,0.3);">
                            <span style="font-size: 40px; font-weight: 800; letter-spacing: 12px;
                                         background: linear-gradient(135deg, #f87171, #fca5a5);
                                         -webkit-background-clip: text; -webkit-text-fill-color: transparent;
                                         font-family: 'Courier New', monospace;">
                                %s
                            </span>
                        </div>
                    </div>
                    <div style="background: rgba(239,68,68,0.08); border-left: 3px solid #ef4444;
                                padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 24px 0;">
                        <p style="color: #fca5a5; font-size: 13px; margin: 0; font-weight: 600;">
                            ⚠️ This action is irreversible
                        </p>
                        <p style="color: #9ca3af; font-size: 12px; margin: 6px 0 0; line-height: 1.6;">
                            Once confirmed, your account and all associated data will be permanently removed.
                            This code expires in 5 minutes.
                        </p>
                    </div>
                    <p style="color: #6b7280; font-size: 12px; margin: 24px 0 0; line-height: 1.6;">
                        If you didn't request this, you can safely ignore this email.
                        Your account will remain active.
                    </p>
                </div>
                <!-- Footer -->
                <div style="padding: 16px 28px; border-top: 1px solid rgba(255,255,255,0.06); text-align: center;">
                    <p style="color: #4b5563; font-size: 11px; margin: 0;">
                        © 2026 ConnectHub · Account Security
                    </p>
                </div>
            </div>
            """.formatted(displayName, otp);
    }

    // ── Self-Deleted Farewell Template ─────────────────────

    private String buildSelfDeletedAccountBody(String username) {
        String displayName = (username != null && !username.isBlank()) ? username : "User";
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto;
                        background: #0f0f1a; border-radius: 16px; overflow: hidden;">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #374151, #4b5563, #6b7280); padding: 32px 24px; text-align: center;">
                    <div style="display: inline-block; background: rgba(255,255,255,0.12);
                                padding: 10px 14px; border-radius: 14px; margin-bottom: 14px;">
                        <span style="font-size: 32px;">👋</span>
                    </div>
                    <h1 style="color: #ffffff; font-size: 26px; margin: 0; font-weight: 800; letter-spacing: -0.5px;">
                        Goodbye from ConnectHub
                    </h1>
                    <p style="color: rgba(255,255,255,0.75); font-size: 13px; margin: 6px 0 0;">
                        Account Deletion Confirmation
                    </p>
                </div>
                <!-- Body -->
                <div style="padding: 32px 28px;">
                    <p style="color: #e5e7eb; font-size: 16px; margin: 0 0 8px; font-weight: 600;">
                        Hi %s,
                    </p>
                    <p style="color: #9ca3af; font-size: 14px; margin: 0 0 24px; line-height: 1.7;">
                        Your ConnectHub account has been <strong style="color: #d1d5db;">permanently deleted</strong>
                        as per your request. We're sorry to see you go.
                    </p>
                    <div style="background: rgba(107,114,128,0.1); border-left: 3px solid #6b7280;
                                padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 0 0 24px;">
                        <p style="color: #d1d5db; font-size: 13px; margin: 0; font-weight: 600;">
                            What's been removed:
                        </p>
                        <ul style="color: #9ca3af; font-size: 13px; margin: 8px 0 0; padding-left: 18px; line-height: 1.8;">
                            <li>Your profile and account data</li>
                            <li>All chat history and room memberships</li>
                            <li>Any active subscriptions</li>
                        </ul>
                    </div>
                    <p style="color: #9ca3af; font-size: 13px; margin: 0 0 24px; line-height: 1.6;">
                        If you change your mind in the future, you're always welcome to create a new account at
                        <a href="http://localhost:3000/register" style="color: #818cf8; text-decoration: none; font-weight: 600;">ConnectHub</a>.
                    </p>
                    <p style="color: #6b7280; font-size: 12px; margin: 0; line-height: 1.6;">
                        Thank you for being part of our community. We wish you all the best.
                    </p>
                </div>
                <!-- Footer -->
                <div style="padding: 16px 28px; border-top: 1px solid rgba(255,255,255,0.06); text-align: center;">
                    <p style="color: #4b5563; font-size: 11px; margin: 0;">
                        © 2026 ConnectHub · Thank you for being with us
                    </p>
                </div>
            </div>
            """.formatted(displayName);
    }

    // ── Subscription Activated Email ───────────────────────

    private void sendSubscriptionActivatedEmail(EmailEvent event)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("🎉 Welcome to ConnectHub PRO! Your Receipt Inside");
        helper.setText(buildSubscriptionActivatedBody(event), true);

        mailSender.send(message);
        log.info("Subscription activated email sent to: {}", event.getToEmail());
    }

    // ── Subscription Cancelled Email ───────────────────────

    private void sendSubscriptionCancelledEmail(EmailEvent event)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("Subscription Cancelled — ConnectHub PRO");
        helper.setText(buildSubscriptionCancelledBody(event), true);

        mailSender.send(message);
        log.info("Subscription cancelled email sent to: {}", event.getToEmail());
    }

    // ── Subscription Activated Template ───────────────────

    private String buildSubscriptionActivatedBody(EmailEvent e) {
        String name          = safe(e.getUsername(), "there");
        String txnId         = safe(e.getTransactionId(), "—");
        String paymentId     = safe(e.getRazorpayPaymentId(), "—");
        String subId         = safe(e.getSubscriptionId(), "—");
        double amount        = e.getAmount()  != null ? e.getAmount()  : 199.00;
        String currency      = safe(e.getCurrency(), "INR");
        String startDateStr  = e.getStartDate()  != null ? formatDate(e.getStartDate())  : "—";
        String endDateStr    = e.getEndDate()    != null ? formatDate(e.getEndDate())    : "—";

        return """
            <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:560px;margin:0 auto;
                        background:#0f0f1a;border-radius:16px;overflow:hidden;">

              <!-- Header -->
              <div style="background:linear-gradient(135deg,#6366f1,#8b5cf6,#c084fc);
                          padding:36px 28px;text-align:center;">
                <div style="display:inline-block;background:rgba(255,255,255,0.15);
                            padding:12px 16px;border-radius:14px;margin-bottom:16px;">
                  <span style="font-size:38px;">👑</span>
                </div>
                <h1 style="color:#fff;font-size:28px;margin:0;font-weight:800;">You're now PRO!</h1>
                <p style="color:rgba(255,255,255,0.85);font-size:14px;margin:6px 0 0;">
                  ConnectHub PRO — Official Receipt
                </p>
              </div>

              <!-- Greeting -->
              <div style="padding:28px 28px 0;">
                <p style="color:#e5e7eb;font-size:16px;margin:0 0 6px;font-weight:600;">Hey %s! 🎉</p>
                <p style="color:#9ca3af;font-size:14px;margin:0 0 24px;line-height:1.7;">
                  Thank you for upgrading to <strong style="color:#c4b5fd;">ConnectHub PRO</strong>.
                  Your payment has been confirmed and your subscription is now active.
                </p>
              </div>

              <!-- Receipt card -->
              <div style="margin:0 28px 24px;background:linear-gradient(135deg,#1e1b4b,#1a1a2e);
                          border:1px solid rgba(99,102,241,0.25);border-radius:14px;overflow:hidden;">

                <div style="padding:14px 20px;background:rgba(99,102,241,0.15);
                            border-bottom:1px solid rgba(99,102,241,0.2);">
                  <p style="color:#a78bfa;font-size:12px;font-weight:700;letter-spacing:0.8px;
                            text-transform:uppercase;margin:0;">Payment Receipt</p>
                </div>

                <div style="padding:20px;">
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                </div>

                <!-- Total -->
                <div style="padding:14px 20px;background:rgba(99,102,241,0.1);
                            border-top:1px solid rgba(99,102,241,0.2);display:flex;
                            justify-content:space-between;align-items:center;">
                  <span style="color:#e5e7eb;font-size:14px;font-weight:700;">Total Paid</span>
                  <span style="color:#c4b5fd;font-size:22px;font-weight:800;">
                    %s %.2f
                  </span>
                </div>
              </div>

              <!-- PRO Features -->
              <div style="margin:0 28px 28px;">
                <p style="color:#6b7280;font-size:12px;font-weight:600;text-transform:uppercase;
                          letter-spacing:0.6px;margin:0 0 12px;">What you unlocked</p>
                <div style="display:flex;gap:10px;flex-wrap:wrap;">
                  %s
                  %s
                  %s
                  %s
                </div>
              </div>

              <!-- CTA -->
              <div style="text-align:center;padding:0 28px 28px;">
                <a href="http://localhost:3000"
                   style="display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6);
                          color:#fff;text-decoration:none;font-size:15px;font-weight:700;
                          padding:14px 44px;border-radius:12px;
                          box-shadow:0 4px 20px rgba(99,102,241,0.4);">
                  Go to ConnectHub →
                </a>
              </div>

              <!-- Footer -->
              <div style="padding:16px 28px;border-top:1px solid rgba(255,255,255,0.06);text-align:center;">
                <p style="color:#4b5563;font-size:11px;margin:0;">
                  © 2026 ConnectHub · Subscription ID: %s
                </p>
              </div>
            </div>
            """.formatted(
                name,
                receiptRow("Plan",         "ConnectHub PRO"),
                receiptRow("Billing Period", startDateStr + " → " + endDateStr),
                receiptRow("Transaction ID", txnId),
                receiptRow("Razorpay ID",   paymentId),
                receiptRow("Status",        "✅ Paid"),
                receiptRow("Auto-Renew",    "Enabled"),
                currency, amount,
                featurePill("💬 Unlimited Rooms"),
                featurePill("📁 50 MB files"),
                featurePill("📜 Full history"),
                featurePill("⚡ Priority support"),
                subId
        );
    }

    // ── Subscription Cancelled Template ───────────────────

    private String buildSubscriptionCancelledBody(EmailEvent e) {
        String name        = safe(e.getUsername(), "there");
        String subId       = safe(e.getSubscriptionId(), "—");
        String endDateStr  = e.getEndDate()     != null ? formatDate(e.getEndDate())     : "—";
        String cancelStr   = e.getCancelledAt() != null ? formatDate(e.getCancelledAt()) : "—";

        return """
            <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:560px;margin:0 auto;
                        background:#0f0f1a;border-radius:16px;overflow:hidden;">

              <!-- Header -->
              <div style="background:linear-gradient(135deg,#374151,#4b5563,#6b7280);
                          padding:32px 28px;text-align:center;">
                <div style="display:inline-block;background:rgba(255,255,255,0.12);
                            padding:10px 14px;border-radius:14px;margin-bottom:14px;">
                  <span style="font-size:34px;">😔</span>
                </div>
                <h1 style="color:#fff;font-size:26px;margin:0;font-weight:800;">Subscription Cancelled</h1>
                <p style="color:rgba(255,255,255,0.75);font-size:13px;margin:6px 0 0;">
                  ConnectHub PRO — Cancellation Confirmation
                </p>
              </div>

              <!-- Body -->
              <div style="padding:28px;">
                <p style="color:#e5e7eb;font-size:16px;margin:0 0 6px;font-weight:600;">Hi %s,</p>
                <p style="color:#9ca3af;font-size:14px;margin:0 0 24px;line-height:1.7;">
                  We've received your cancellation request for <strong style="color:#d1d5db;">ConnectHub PRO</strong>.
                  Your PRO access remains active until the end of your current billing period.
                </p>

                <!-- Cancellation details -->
                <div style="background:linear-gradient(135deg,#1e1b4b,#1a1a2e);
                            border:1px solid rgba(99,102,241,0.2);border-radius:14px;
                            overflow:hidden;margin-bottom:24px;">
                  <div style="padding:12px 20px;background:rgba(99,102,241,0.1);
                              border-bottom:1px solid rgba(99,102,241,0.15);">
                    <p style="color:#a78bfa;font-size:12px;font-weight:700;text-transform:uppercase;
                              letter-spacing:0.8px;margin:0;">Cancellation Details</p>
                  </div>
                  <div style="padding:20px;">
                    %s
                    %s
                    %s
                  </div>
                </div>

                <!-- Access notice -->
                <div style="background:rgba(234,179,8,0.08);border-left:3px solid #eab308;
                            padding:14px 16px;border-radius:0 8px 8px 0;margin-bottom:24px;">
                  <p style="color:#fde047;font-size:13px;margin:0;font-weight:600;">📅 Important</p>
                  <p style="color:#9ca3af;font-size:13px;margin:6px 0 0;line-height:1.6;">
                    You retain full PRO access until <strong style="color:#fde047;">%s</strong>.
                    After this date your account will revert to the Free plan automatically.
                  </p>
                </div>

                <!-- Re-subscribe CTA -->
                <div style="text-align:center;">
                  <a href="http://localhost:3000/subscription"
                     style="display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6);
                            color:#fff;text-decoration:none;font-size:14px;font-weight:700;
                            padding:13px 36px;border-radius:12px;
                            box-shadow:0 4px 20px rgba(99,102,241,0.35);">
                    Re-subscribe to PRO
                  </a>
                </div>
              </div>

              <!-- Footer -->
              <div style="padding:16px 28px;border-top:1px solid rgba(255,255,255,0.06);text-align:center;">
                <p style="color:#4b5563;font-size:11px;margin:0;">
                  © 2026 ConnectHub · Subscription ID: %s
                </p>
              </div>
            </div>
            """.formatted(
                name,
                receiptRow("Plan",              "ConnectHub PRO"),
                receiptRow("Cancelled On",       cancelStr),
                receiptRow("Access Until",       endDateStr),
                endDateStr,
                subId
        );
    }

    // ── Registration OTP Email ────────────────────────────

    private void sendRegistrationOtpEmail(EmailEvent event) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, FROM_NAME);
        helper.setTo(event.getToEmail());
        helper.setSubject("Verify your email to join ConnectHub");

        String htmlContent = getRegistrationOtpTemplate(event.getOtp());
        helper.setText(htmlContent, true);

        mailSender.send(message);
        log.debug("Registration OTP email sent to {}", event.getToEmail());
    }

    private String getRegistrationOtpTemplate(String otp) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Inter', -apple-system, sans-serif; background-color: #09090b; color: #f4f4f5; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 0 auto; padding: 40px 20px; }
                    .card { background-color: #18181b; border: 1px solid #27272a; border-radius: 24px; padding: 40px; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                    .logo { margin-bottom: 24px; font-size: 24px; font-weight: 800; letter-spacing: -0.5px; }
                    .logo span { color: #6366f1; }
                    h1 { color: #ffffff; font-size: 24px; margin-bottom: 12px; }
                    p { color: #a1a1aa; font-size: 15px; line-height: 1.6; margin-bottom: 30px; }
                    .otp-box { background: linear-gradient(135deg, rgba(99,102,241,0.1), rgba(168,85,247,0.1)); border: 1px solid rgba(168,85,247,0.2); border-radius: 16px; padding: 24px; margin: 30px 0; }
                    .otp-code { font-family: 'Courier New', monospace; font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #c084fc; margin: 0; }
                    .warning { background-color: rgba(245,158,11,0.1); border-radius: 12px; padding: 16px; margin-bottom: 30px; border: 1px solid rgba(245,158,11,0.2); }
                    .warning p { color: #fcd34d; font-size: 13px; margin: 0; }
                    .footer { text-align: center; margin-top: 40px; color: #52525b; font-size: 13px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="card">
                        <div class="logo">Connect<span>Hub</span></div>
                        <h1>Welcome! Let's verify your email</h1>
                        <p>You're almost there! We just need to verify this email address belongs to you before you can start chatting.</p>
                        
                        <div class="otp-box">
                            <p style="margin-bottom: 10px; font-size: 12px; text-transform: uppercase; letter-spacing: 1px; color: #a1a1aa;">Verification Code</p>
                            <p class="otp-code">""" + otp + """
                            </p>
                        </div>
                        
                        <div class="warning">
                            <p>This code will expire in 5 minutes. If you didn't request this, please ignore this email.</p>
                        </div>
                        
                        <p style="margin-bottom: 0; font-size: 14px;">See you inside!<br>The ConnectHub Team</p>
                    </div>
                    <div class="footer">
                        &copy; 2026 ConnectHub. All rights reserved.
                    </div>
                </div>
            </body>
            </html>
            """;
    }

    // ── Shared Helpers ─────────────────────────────────────

    private String receiptRow(String label, String value) {
        return """
            <div style="display:flex;justify-content:space-between;padding:8px 0;
                        border-bottom:1px solid rgba(255,255,255,0.05);">
              <span style="color:#6b7280;font-size:13px;">%s</span>
              <span style="color:#e5e7eb;font-size:13px;font-weight:600;">%s</span>
            </div>""".formatted(label, value);
    }

    private String featurePill(String text) {
        return """
            <span style="display:inline-block;background:rgba(99,102,241,0.15);
                         border:1px solid rgba(99,102,241,0.25);color:#c4b5fd;
                         font-size:12px;font-weight:600;padding:6px 12px;
                         border-radius:20px;margin:4px 4px 4px 0;">%s</span>""".formatted(text);
    }

    private String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private String formatDate(java.time.LocalDateTime dt) {
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }
}
