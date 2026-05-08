package com.connecthub.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity to store OTP records for password reset flow.
 * Separate table — does NOT modify the existing User entity.
 */
@Entity
@Table(name = "password_reset_otp", indexes = {
        @Index(name = "idx_otp_email", columnList = "email"),
        @Index(name = "idx_otp_email_otp", columnList = "email, otp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 6)
    private String otp;

    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if this OTP has expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryTime);
    }

    /**
     * Check if max attempts have been exhausted.
     */
    public boolean isMaxAttemptsReached() {
        return this.attempts >= 3;
    }
}
