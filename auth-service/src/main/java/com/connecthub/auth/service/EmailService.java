package com.connecthub.auth.service;

/**
 * Abstraction for email sending — decoupled from both JavaMailSender and RabbitMQ.
 * Callers don't know whether emails go through a queue or are sent directly.
 */
public interface EmailService {

    /**
     * Send a registration OTP email to the given recipient.
     *
     * @param toEmail   recipient email address
     * @param otp       the 6-digit OTP code
     */
    void sendRegistrationOtpEmail(String toEmail, String otp);

    /**
     * Send a password reset OTP email to the given recipient.
     *
     * @param toEmail   recipient email address
     * @param otp       the 6-digit OTP code
     */
    void sendOtpEmail(String toEmail, String otp);

    /**
     * Send a welcome email after successful registration.
     *
     * @param toEmail   recipient email address
     * @param username  the user's display name / username
     */
    void sendWelcomeEmail(String toEmail, String username);

    /**
     * Notify user that their account has been suspended by an admin.
     *
     * @param toEmail   recipient email address
     * @param username  the user's display name / username
     */
    void sendAccountSuspendedEmail(String toEmail, String username);

    /**
     * Notify user that their account has been permanently deleted by an admin.
     *
     * @param toEmail   recipient email address
     * @param username  the user's display name / username
     */
    void sendAccountDeletedEmail(String toEmail, String username);

    /**
     * Send a 6-digit OTP for self-service account deletion verification.
     *
     * @param toEmail   recipient email address
     * @param otp       the 6-digit OTP code
     * @param username  the user's display name / username
     */
    void sendAccountDeletionOtpEmail(String toEmail, String otp, String username);

    /**
     * Send a farewell email after a user permanently deletes their own account.
     *
     * @param toEmail   recipient email address
     * @param username  the user's display name / username
     */
    void sendSelfDeletedAccountEmail(String toEmail, String username);
}
