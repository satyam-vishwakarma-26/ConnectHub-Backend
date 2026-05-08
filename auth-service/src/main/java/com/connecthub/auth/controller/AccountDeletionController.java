package com.connecthub.auth.controller;

import com.connecthub.auth.dto.request.ForgotPasswordRequest;
import com.connecthub.auth.dto.request.VerifyOtpRequest;
import com.connecthub.auth.dto.response.ApiResponse;
import com.connecthub.auth.security.CustomUserDetails;
import com.connecthub.auth.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for self-service account deletion.
 * All endpoints require authentication (JWT).
 *
 * Flow:
 *   POST /auth/account/delete/request-otp   → send 6-digit OTP to user's email
 *   POST /auth/account/delete/verify-otp    → verify the OTP
 *   POST /auth/account/delete/confirm       → permanently delete the account
 */
@RestController
@RequestMapping("/auth/account/delete")
@RequiredArgsConstructor
@Tag(name = "Account Deletion", description = "Self-service account deletion with OTP verification")
public class AccountDeletionController {

    private final AccountDeletionService accountDeletionService;

    // ── POST /auth/account/delete/request-otp ─────────────

    @PostMapping("/request-otp")
    @Operation(summary = "Request a deletion OTP — sends a 6-digit code to the user's email",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> requestDeletionOtp(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ForgotPasswordRequest request) {

        String message = accountDeletionService.sendDeletionOtp(
                currentUser.getUserId(), request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ── POST /auth/account/delete/verify-otp ──────────────

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify the 6-digit deletion OTP",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> verifyDeletionOtp(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody VerifyOtpRequest request) {

        String message = accountDeletionService.verifyDeletionOtp(
                currentUser.getUserId(), request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ── POST /auth/account/delete/confirm ─────────────────

    @PostMapping("/confirm")
    @Operation(summary = "Permanently delete the account after OTP verification",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> confirmDeletion(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ForgotPasswordRequest request) {

        String message = accountDeletionService.confirmDeletion(
                currentUser.getUserId(), request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }
}
