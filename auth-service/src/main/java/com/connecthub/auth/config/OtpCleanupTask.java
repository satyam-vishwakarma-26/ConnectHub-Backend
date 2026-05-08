package com.connecthub.auth.config;

import com.connecthub.auth.repository.PasswordResetOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled task to automatically clean up expired OTP records.
 * Runs every 30 minutes to prevent table bloat.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupTask {

    private final PasswordResetOtpRepository otpRepository;

    @Scheduled(fixedRate = 1800000) // Every 30 minutes
    @Transactional
    public void cleanupExpiredOtps() {
        LocalDateTime cutoff = LocalDateTime.now();
        otpRepository.deleteExpiredOtps(cutoff);
        log.debug("Expired OTPs cleanup executed at {}", cutoff);
    }
}
