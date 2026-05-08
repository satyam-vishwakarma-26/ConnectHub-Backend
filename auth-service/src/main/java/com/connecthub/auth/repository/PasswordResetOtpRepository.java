package com.connecthub.auth.repository;

import com.connecthub.auth.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    /**
     * Find the latest valid (not used) OTP for the given email.
     */
    Optional<PasswordResetOtp> findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(String email);

    /**
     * Invalidate all existing OTPs for an email (used before generating a new one).
     */
    @Modifying
    @Query("UPDATE PasswordResetOtp o SET o.isUsed = true WHERE o.email = :email AND o.isUsed = false")
    void invalidateAllOtpsByEmail(@Param("email") String email);

    /**
     * Cleanup expired OTPs older than the given cutoff time.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetOtp o WHERE o.expiryTime < :cutoff")
    void deleteExpiredOtps(@Param("cutoff") LocalDateTime cutoff);
}
