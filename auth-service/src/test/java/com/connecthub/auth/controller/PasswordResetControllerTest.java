package com.connecthub.auth.controller;

import com.connecthub.auth.dto.request.ForgotPasswordRequest;
import com.connecthub.auth.dto.request.ResetPasswordRequest;
import com.connecthub.auth.dto.request.VerifyOtpRequest;
import com.connecthub.auth.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetController Tests")
class PasswordResetControllerTest {

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetController controller;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("forgotPassword returns success message")
    void forgotPassword_returnsSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");

        when(passwordResetService.forgotPassword(any())).thenReturn("OTP sent to your email");

        ResponseEntity<?> response = controller.forgotPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("verifyOtp returns success message")
    void verifyOtp_returnsSuccess() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        when(passwordResetService.verifyOtp(any())).thenReturn("OTP verified successfully");

        ResponseEntity<?> response = controller.verifyOtp(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("resetPassword returns success message")
    void resetPassword_returnsSuccess() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("test@example.com");
        request.setNewPassword("newPassword123");
        request.setConfirmPassword("newPassword123");

        when(passwordResetService.resetPassword(any())).thenReturn("Password reset successfully");

        ResponseEntity<?> response = controller.resetPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}