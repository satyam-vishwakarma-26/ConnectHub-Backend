package com.connecthub.auth.controller;

import com.connecthub.auth.dto.request.ForgotPasswordRequest;
import com.connecthub.auth.dto.request.ResetPasswordRequest;
import com.connecthub.auth.dto.request.VerifyOtpRequest;
import com.connecthub.auth.dto.response.ApiResponse;
import com.connecthub.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for the Forgot Password / OTP Reset flow.
 * All endpoints are PUBLIC (no auth required).
 * Completely separate from AuthController — no existing code modified.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Password Reset", description = "Forgot password, OTP verification and password reset endpoints")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // ── POST /auth/forgot-password ────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset OTP via email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        String message = passwordResetService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ── POST /auth/verify-otp ─────────────────────────────

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify the 6-digit OTP sent to email")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        String message = passwordResetService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ── POST /auth/reset-password ─────────────────────────

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password after OTP verification")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        String message = passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }
}
