package com.connecthub.auth.controller;

import com.connecthub.auth.dto.request.ForgotPasswordRequest;
import com.connecthub.auth.dto.request.VerifyOtpRequest;
import com.connecthub.auth.security.CustomUserDetails;
import com.connecthub.auth.service.AccountDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountDeletionController Tests")
class AccountDeletionControllerTest {

    @Mock
    private AccountDeletionService accountDeletionService;

    @InjectMocks
    private AccountDeletionController controller;

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(1L);
    }

    @Test
    @DisplayName("requestDeletionOtp returns success message")
    void requestDeletionOtp_returnsSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");

        when(accountDeletionService.sendDeletionOtp(eq(1L), eq("test@example.com"))).thenReturn("Deletion OTP sent to your email");

        ResponseEntity<?> response = controller.requestDeletionOtp(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("verifyDeletionOtp returns success message")
    void verifyDeletionOtp_returnsSuccess() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        when(accountDeletionService.verifyDeletionOtp(eq(1L), eq("test@example.com"), eq("123456"))).thenReturn("Account deletion verified");

        ResponseEntity<?> response = controller.verifyDeletionOtp(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("confirmDeletion returns success message")
    void confirmDeletion_returnsSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");

        when(accountDeletionService.confirmDeletion(eq(1L), eq("test@example.com"))).thenReturn("Account deleted successfully");

        ResponseEntity<?> response = controller.confirmDeletion(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}