package com.connecthub.auth.service.impl;

import com.connecthub.auth.dto.request.ForgotPasswordRequest;
import com.connecthub.auth.dto.request.ResetPasswordRequest;
import com.connecthub.auth.dto.request.VerifyOtpRequest;
import com.connecthub.auth.entity.PasswordResetOtp;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.BadRequestException;
import com.connecthub.auth.exception.ResourceNotFoundException;
import com.connecthub.auth.repository.PasswordResetOtpRepository;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.service.EmailService;
import com.connecthub.auth.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── 1. Forgot Password — Generate & Send OTP ──────────

    @Override
    public String forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Validate that the email exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account found with email: " + email));

        // Check if the user is a LOCAL provider (OAuth users cannot reset password)
        if (user.getProvider() != User.AuthProvider.LOCAL) {
            throw new BadRequestException(
                    "This account uses " + user.getProvider() + " login. Password reset is not available.");
        }

        // Invalidate any previously active OTPs for this email
        otpRepository.invalidateAllOtpsByEmail(email);

        // Generate a cryptographically secure 6-digit OTP
        String otp = generateOtp();

        // Create and persist the OTP record
        PasswordResetOtp otpEntity = PasswordResetOtp.builder()
                .email(email)
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES))
                .attempts(0)
                .isVerified(false)
                .isUsed(false)
                .build();

        otpRepository.save(otpEntity);
        log.info("OTP generated for password reset: email={}", email);

        // Send OTP via email (async)
        emailService.sendOtpEmail(email, otp);

        return "OTP has been sent to your registered email address.";
    }

    // ── 2. Verify OTP ─────────────────────────────────────

    @Override
    public String verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String submittedOtp = request.getOtp().trim();

        // Fetch the latest active OTP for this email
        PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BadRequestException(
                        "No active OTP found for this email. Please request a new one."));

        // Check if already verified
        if (otpEntity.getIsVerified()) {
            throw new BadRequestException("OTP has already been verified. Please proceed to reset your password.");
        }

        // Check if max attempts reached
        if (otpEntity.isMaxAttemptsReached()) {
            otpEntity.setIsUsed(true); // Invalidate OTP
            otpRepository.save(otpEntity);
            throw new BadRequestException(
                    "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Check if expired
        if (otpEntity.isExpired()) {
            otpEntity.setIsUsed(true); // Mark as used
            otpRepository.save(otpEntity);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        // Increment attempt count
        otpEntity.setAttempts(otpEntity.getAttempts() + 1);

        // Verify OTP value
        if (!otpEntity.getOtp().equals(submittedOtp)) {
            otpRepository.save(otpEntity);
            int remaining = MAX_ATTEMPTS - otpEntity.getAttempts();
            throw new BadRequestException(
                    "Invalid OTP. You have " + remaining + " attempt(s) remaining.");
        }

        // OTP is correct — mark as verified
        otpEntity.setIsVerified(true);
        otpRepository.save(otpEntity);
        log.info("OTP verified successfully for email={}", email);

        return "OTP verified successfully. You can now reset your password.";
    }

    // ── 3. Reset Password ─────────────────────────────────

    @Override
    public String resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        // Fetch the verified OTP for this email
        PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BadRequestException(
                        "No verified OTP found. Please complete OTP verification first."));

        // Ensure the OTP was actually verified
        if (!otpEntity.getIsVerified()) {
            throw new BadRequestException("OTP has not been verified. Please verify your OTP first.");
        }

        // Double-check expiry (prevent stale verified OTPs from being used)
        if (otpEntity.isExpired()) {
            otpEntity.setIsUsed(true);
            otpRepository.save(otpEntity);
            throw new BadRequestException("Session expired. Please request a new OTP.");
        }

        // Fetch the user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        // Update password with BCrypt hash
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setRefreshToken(null); // Invalidate all active sessions
        userRepository.save(user);

        // Invalidate the OTP after successful password reset
        otpEntity.setIsUsed(true);
        otpRepository.save(otpEntity);

        log.info("Password reset successfully for email={}", email);
        return "Password has been reset successfully. You can now login with your new password.";
    }

    // ── Private Helpers ───────────────────────────────────

    /**
     * Generate a cryptographically secure 6-digit numeric OTP.
     */
    private String generateOtp() {
        int otp = 100000 + SECURE_RANDOM.nextInt(900000); // Range: 100000 - 999999
        return String.valueOf(otp);
    }
}
