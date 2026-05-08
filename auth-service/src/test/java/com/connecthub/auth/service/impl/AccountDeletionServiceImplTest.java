package com.connecthub.auth.service.impl;

import com.connecthub.auth.entity.PasswordResetOtp;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.BadRequestException;
import com.connecthub.auth.repository.PasswordResetOtpRepository;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetOtpRepository otpRepository;

    @Mock
    private EmailService emailService;

    private AccountDeletionServiceImpl service;
    private User testUser;
    private PasswordResetOtp testOtp;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionServiceImpl(userRepository, otpRepository, emailService);
        testUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .username("testuser")
                .passwordHash("hashed")
                .build();
        testOtp = PasswordResetOtp.builder()
                .email("user@test.com")
                .otp("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isVerified(false)
                .isUsed(false)
                .build();
    }

    @Test
    void sendDeletionOtp_success_sendsOtpEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.save(any())).thenReturn(testOtp);

        String result = service.sendDeletionOtp(1L, "user@test.com");

        assertNotNull(result);
        verify(emailService, times(1)).sendAccountDeletionOtpEmail(any(), any(), any());
    }

    @Test
    void sendDeletionOtp_emailMismatch_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () ->
                service.sendDeletionOtp(1L, "wrong@test.com"));
    }

    @Test
    void verifyDeletionOtp_success_verifiesOtp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.of(testOtp));
        when(otpRepository.save(any())).thenReturn(testOtp);

        String result = service.verifyDeletionOtp(1L, "user@test.com", "123456");

        assertNotNull(result);
        assertTrue(result.contains("verified"));
    }

    @Test
    void verifyDeletionOtp_emailMismatch_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () ->
                service.verifyDeletionOtp(1L, "wrong@test.com", "123456"));
    }

    @Test
    void verifyDeletionOtp_noOtpFound_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                service.verifyDeletionOtp(1L, "user@test.com", "123456"));
    }

    @Test
    void verifyDeletionOtp_alreadyVerified_throwsBadRequest() {
        testOtp.setIsVerified(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.of(testOtp));

        assertThrows(BadRequestException.class, () ->
                service.verifyDeletionOtp(1L, "user@test.com", "123456"));
    }

    @Test
    void verifyDeletionOtp_expired_throwsBadRequest() {
        testOtp.setExpiryTime(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.of(testOtp));
        when(otpRepository.save(any())).thenReturn(testOtp);

        assertThrows(BadRequestException.class, () ->
                service.verifyDeletionOtp(1L, "user@test.com", "123456"));
    }

    @Test
    void verifyDeletionOtp_invalidOtp_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.of(testOtp));
        when(otpRepository.save(any())).thenReturn(testOtp);

        assertThrows(BadRequestException.class, () ->
                service.verifyDeletionOtp(1L, "user@test.com", "wrong"));
    }

    @Test
    void confirmDeletion_success_deletesUser() {
        testOtp.setIsVerified(true);
        testOtp.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.of(testOtp));
        when(otpRepository.save(any())).thenReturn(testOtp);
        doNothing().when(userRepository).delete(testUser);

        String result = service.confirmDeletion(1L, "user@test.com");

        assertNotNull(result);
        assertTrue(result.contains("deleted"));
        verify(userRepository, times(1)).delete(testUser);
    }

    @Test
    void confirmDeletion_emailMismatch_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () ->
                service.confirmDeletion(1L, "wrong@test.com"));
    }

    @Test
    void confirmDeletion_noVerifiedOtp_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                service.confirmDeletion(1L, "user@test.com"));
    }

    @Test
    void confirmDeletion_otpNotVerified_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.of(testOtp));

        assertThrows(BadRequestException.class, () ->
                service.confirmDeletion(1L, "user@test.com"));
    }

    @Test
    void confirmDeletion_otpExpired_throwsBadRequest() {
        testOtp.setIsVerified(true);
        testOtp.setExpiryTime(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(any())).thenReturn(Optional.of(testOtp));
        when(otpRepository.save(any())).thenReturn(testOtp);

        assertThrows(BadRequestException.class, () ->
                service.confirmDeletion(1L, "user@test.com"));
    }
}