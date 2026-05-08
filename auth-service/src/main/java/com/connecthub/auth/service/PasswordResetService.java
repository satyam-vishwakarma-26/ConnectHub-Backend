package com.connecthub.auth.service;

import com.connecthub.auth.dto.request.ForgotPasswordRequest;
import com.connecthub.auth.dto.request.ResetPasswordRequest;
import com.connecthub.auth.dto.request.VerifyOtpRequest;

/**
 * Service interface for the complete forgot-password flow.
 * Completely separate from AuthService — no existing code modified.
 */
public interface PasswordResetService {

    /**
     * Generate and send an OTP to the user's email.
     *
     * @param request contains the user's email
     * @return a generic success message (does not reveal if email exists)
     */
    String forgotPassword(ForgotPasswordRequest request);

    /**
     * Verify the OTP submitted by the user.
     *
     * @param request contains email and otp
     * @return a success message if OTP is valid
     */
    String verifyOtp(VerifyOtpRequest request);

    /**
     * Reset the user's password after successful OTP verification.
     *
     * @param request contains email, newPassword, and confirmPassword
     * @return a success message upon password update
     */
    String resetPassword(ResetPasswordRequest request);
}
