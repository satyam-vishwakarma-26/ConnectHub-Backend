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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetServiceImpl Tests")
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetOtpRepository otpRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetServiceImpl(userRepository, otpRepository, emailService, passwordEncoder);
    }

    @Test
    @DisplayName("forgotPassword sends OTP when email exists")
    void forgotPassword_emailExists_sendsOtp() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setProvider(User.AuthProvider.LOCAL);
        
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = service.forgotPassword(new ForgotPasswordRequest("test@example.com"));

        assertThat(result).isNotNull();
        verify(otpRepository).save(any());
    }

    @Test
    @DisplayName("forgotPassword throws when user not found")
    void forgotPassword_userNotFound_throwsException() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forgotPassword(new ForgotPasswordRequest("notfound@example.com")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("forgotPassword throws when OAuth user tries to reset")
    void forgotPassword_oAuthUser_throwsException() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setProvider(User.AuthProvider.GOOGLE);
        
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.forgotPassword(new ForgotPasswordRequest("test@example.com")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("verifyOtp returns success when OTP is valid")
    void verifyOtp_validOtp_returnsSuccess() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("test@example.com")
                .otp("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isVerified(false)
                .isUsed(false)
                .build();
        
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("test@example.com"))
                .thenReturn(Optional.of(otp));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = service.verifyOtp(new VerifyOtpRequest("test@example.com", "123456"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("verifyOtp throws when OTP expired")
    void verifyOtp_expired_throwsException() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("test@example.com")
                .otp("123456")
                .expiryTime(LocalDateTime.now().minusMinutes(5))
                .attempts(0)
                .isVerified(false)
                .isUsed(false)
                .build();
        
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("test@example.com"))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.verifyOtp(new VerifyOtpRequest("test@example.com", "123456")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("verifyOtp throws when already verified")
    void verifyOtp_alreadyVerified_throwsException() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("test@example.com")
                .otp("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isVerified(true)
                .isUsed(false)
                .build();
        
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("test@example.com"))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.verifyOtp(new VerifyOtpRequest("test@example.com", "123456")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("resetPassword updates password")
    void resetPassword_validRequest_updatesPassword() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setPasswordHash("oldHash");
        
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("test@example.com")
                .otp("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isVerified(true)
                .isUsed(false)
                .build();
        
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("test@example.com"))
                .thenReturn(Optional.of(otp));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encodedPassword");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("test@example.com");
        request.setNewPassword("newPassword123");
        request.setConfirmPassword("newPassword123");

        String result = service.resetPassword(request);

        assertThat(result).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("resetPassword throws when passwords don't match")
    void resetPassword_passwordsMismatch_throwsException() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("test@example.com");
        request.setNewPassword("newPassword123");
        request.setConfirmPassword("differentPassword");

        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("resetPassword throws when OTP not verified")
    void resetPassword_otpNotVerified_throwsException() {
        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("test@example.com")
                .otp("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isVerified(false)
                .isUsed(false)
                .build();
        
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc("test@example.com"))
                .thenReturn(Optional.of(otp));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("test@example.com");
        request.setNewPassword("newPassword123");
        request.setConfirmPassword("newPassword123");

        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOf(BadRequestException.class);
    }
}