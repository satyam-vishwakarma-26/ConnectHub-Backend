package com.connecthub.auth.repository;

import com.connecthub.auth.entity.PasswordResetOtp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetOtpRepositoryTest {

    @Mock
    private PasswordResetOtpRepository otpRepository;

    @Test
    void findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc_whenOtpExists_returnsOtp() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email("test@test.com")
                .otp("123456")
                .isUsed(false)
                .build();
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("test@test.com"))
                .thenReturn(Optional.of(otp));

        Optional<PasswordResetOtp> result = otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("test@test.com");

        assertTrue(result.isPresent());
        assertEquals("123456", result.get().getOtp());
    }

    @Test
    void findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc_whenNotFound_returnsEmpty() {
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("notfound@test.com"))
                .thenReturn(Optional.empty());

        Optional<PasswordResetOtp> result = otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("notfound@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void save_otp_returnsSavedOtp() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email("test@test.com")
                .otp("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
        when(otpRepository.save(any())).thenReturn(otp);

        PasswordResetOtp result = otpRepository.save(otp);

        assertNotNull(result);
    }

    @Test
    void invalidateAllOtpsByEmail_marksAllAsUsed() {
        doNothing().when(otpRepository).invalidateAllOtpsByEmail("test@test.com");

        assertDoesNotThrow(() -> otpRepository.invalidateAllOtpsByEmail("test@test.com"));
    }

    @Test
    void deleteExpiredOtps_deletesExpiredOtps() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        doNothing().when(otpRepository).deleteExpiredOtps(cutoff);

        assertDoesNotThrow(() -> otpRepository.deleteExpiredOtps(cutoff));
    }

    @Test
    void findById_whenExists_returnsOtp() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("test@test.com")
                .build();
        when(otpRepository.findById(1L)).thenReturn(Optional.of(otp));

        Optional<PasswordResetOtp> result = otpRepository.findById(1L);

        assertTrue(result.isPresent());
    }

    @Test
    void findById_whenNotFound_returnsEmpty() {
        when(otpRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<PasswordResetOtp> result = otpRepository.findById(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_returnsAllOtps() {
        when(otpRepository.findAll()).thenReturn(java.util.List.of(
                PasswordResetOtp.builder().id(1L).build(),
                PasswordResetOtp.builder().id(2L).build()
        ));

        var result = otpRepository.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void delete_deletesOtp() {
        PasswordResetOtp otp = PasswordResetOtp.builder().id(1L).build();
        doNothing().when(otpRepository).delete(otp);

        assertDoesNotThrow(() -> otpRepository.delete(otp));
    }
}