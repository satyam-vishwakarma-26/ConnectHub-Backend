package com.connecthub.auth.service;

/**
 * Service interface for the self-service account deletion flow.
 *
 * Flow:
 *   1. User requests OTP → sendDeletionOtp(email)
 *   2. User verifies OTP → verifyDeletionOtp(email, otp)
 *   3. User confirms permanent deletion → confirmDeletion(email)
 *
 * The deletion confirmation email is sent asynchronously via RabbitMQ.
 */
public interface AccountDeletionService {

    /**
     * Generate and send a 6-digit OTP for account deletion verification.
     *
     * @param userId the authenticated user's ID (from JWT)
     * @param email  the email the user entered in the deletion form
     * @return a success message
     */
    String sendDeletionOtp(Long userId, String email);

    /**
     * Verify the OTP submitted by the user for account deletion.
     *
     * @param userId the authenticated user's ID
     * @param email  the email used during OTP request
     * @param otp    the 6-digit OTP
     * @return a success message
     */
    String verifyDeletionOtp(Long userId, String email, String otp);

    /**
     * Permanently delete the user's account after successful OTP verification.
     * Sends a farewell email via RabbitMQ.
     *
     * @param userId the authenticated user's ID
     * @param email  the email used during OTP verification
     * @return a success message
     */
    String confirmDeletion(Long userId, String email);
}
