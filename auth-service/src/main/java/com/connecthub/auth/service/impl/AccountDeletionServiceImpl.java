package com.connecthub.auth.service.impl;

import com.connecthub.auth.entity.PasswordResetOtp;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.BadRequestException;
import com.connecthub.auth.exception.ResourceNotFoundException;
import com.connecthub.auth.repository.PasswordResetOtpRepository;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.service.AccountDeletionService;
import com.connecthub.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Implementation of the self-service account deletion flow.
 *
 * Re-uses the {@link PasswordResetOtp} table (OTP type is distinguished
 * by caller context, not by a column — kept simple and DRY).
 *
 * Security:
 *  - The email MUST match the authenticated user's email.
 *  - OTP is 6-digit, expires in 5 minutes, max 3 attempts.
 *  - Deletion is irreversible; a farewell email is sent via RabbitMQ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final EmailService emailService;

    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── 1. Send Deletion OTP ──────────────────────────────

    @Override
    public String sendDeletionOtp(Long userId, String email) {
        String normalizedEmail = email.trim().toLowerCase();

        // Verify the email belongs to the authenticated user
        User user = findUserById(userId);
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new BadRequestException(
                    "The email you entered does not match your registered email address.");
        }

        // Invalidate any existing OTPs for this email
        otpRepository.invalidateAllOtpsByEmail(normalizedEmail);

        // Generate and persist a new OTP
        String otp = generateOtp();
        PasswordResetOtp otpEntity = PasswordResetOtp.builder()
                .email(normalizedEmail)
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES))
                .attempts(0)
                .isVerified(false)
                .isUsed(false)
                .build();
        otpRepository.save(otpEntity);

        log.info("Account deletion OTP generated for email={}", normalizedEmail);

        // Send OTP via email (async — RabbitMQ)
        emailService.sendAccountDeletionOtpEmail(normalizedEmail, otp, user.getUsername());

        return "A 6-digit verification code has been sent to your email.";
    }

    // ── 2. Verify Deletion OTP ────────────────────────────

    @Override
    public String verifyDeletionOtp(Long userId, String email, String otp) {
        String normalizedEmail = email.trim().toLowerCase();

        // Verify email ownership
        User user = findUserById(userId);
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("Email does not match your account.");
        }

        // Fetch latest active OTP
        PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new BadRequestException(
                        "No active OTP found. Please request a new one."));

        if (otpEntity.getIsVerified()) {
            throw new BadRequestException("OTP already verified. Please proceed to confirm deletion.");
        }

        if (otpEntity.isMaxAttemptsReached()) {
            otpEntity.setIsUsed(true);
            otpRepository.save(otpEntity);
            throw new BadRequestException(
                    "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (otpEntity.isExpired()) {
            otpEntity.setIsUsed(true);
            otpRepository.save(otpEntity);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        otpEntity.setAttempts(otpEntity.getAttempts() + 1);

        if (!otpEntity.getOtp().equals(otp.trim())) {
            otpRepository.save(otpEntity);
            int remaining = MAX_ATTEMPTS - otpEntity.getAttempts();
            throw new BadRequestException(
                    "Invalid OTP. You have " + remaining + " attempt(s) remaining.");
        }

        // Mark as verified
        otpEntity.setIsVerified(true);
        otpRepository.save(otpEntity);
        log.info("Account deletion OTP verified for email={}", normalizedEmail);

        return "OTP verified successfully. You may now confirm account deletion.";
    }

    // ── 3. Confirm Deletion ───────────────────────────────

    @Override
    public String confirmDeletion(Long userId, String email) {
        String normalizedEmail = email.trim().toLowerCase();

        // Verify email ownership
        User user = findUserById(userId);
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("Email does not match your account.");
        }

        // Ensure a verified OTP exists
        PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new BadRequestException(
                        "No verified OTP found. Please complete OTP verification first."));

        if (!otpEntity.getIsVerified()) {
            throw new BadRequestException("OTP has not been verified. Please verify first.");
        }

        if (otpEntity.isExpired()) {
            otpEntity.setIsUsed(true);
            otpRepository.save(otpEntity);
            throw new BadRequestException("Session expired. Please restart the deletion process.");
        }

        // Capture user info before deletion (for the farewell email)
        String username = user.getUsername();

        // Invalidate the OTP
        otpEntity.setIsUsed(true);
        otpRepository.save(otpEntity);

        // Delete the user permanently
        userRepository.delete(user);
        log.info("User self-deleted: id={}, email={}", userId, normalizedEmail);

        // Send farewell email via RabbitMQ (async — user row is gone but queue is async)
        emailService.sendSelfDeletedAccountEmail(normalizedEmail, username);

        return "Your account has been permanently deleted. We're sorry to see you go.";
    }

    // ── Private Helpers ───────────────────────────────────

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private String generateOtp() {
        int otp = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }
}
