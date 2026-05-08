package com.connecthub.auth.controller;

import com.connecthub.auth.dto.request.*;
import com.connecthub.auth.dto.response.ApiResponse;
import com.connecthub.auth.dto.response.AuthResponse;
import com.connecthub.auth.dto.response.UserResponse;
import com.connecthub.auth.security.CustomUserDetails;
import com.connecthub.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, profile, status and admin endpoints")
public class AuthController {

    private final AuthService authService;

    // ── Public: Register ──────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }
    @PostMapping("/register/request-otp")
    @Operation(summary = "Request OTP for email verification during registration")
    public ResponseEntity<ApiResponse<Void>> requestRegistrationOtp(
            @Valid @RequestBody ForgotPasswordRequest request) { // Reusing DTO that has email
        authService.requestRegistrationOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your email", null));
    }

    @PostMapping("/register/verify-otp")
    @Operation(summary = "Verify OTP for registration")
    public ResponseEntity<ApiResponse<Void>> verifyRegistrationOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyRegistrationOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
    }
    // ── Public: Login ─────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // ── Public: Refresh Token ─────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    // ── Secured: Logout ───────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Logout — invalidates refresh token",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        authService.logout(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    // ── Secured: Get My Profile ───────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        UserResponse user = authService.getUserById(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ── Secured: Get Any User Profile ────────────────────

    @GetMapping("/profile/{userId}")
    @Operation(summary = "Get user profile by ID",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long userId) {

        UserResponse user = authService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ── Secured: Update Profile ───────────────────────────

    @PutMapping("/profile")
    @Operation(summary = "Update current user profile",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserResponse user = authService.updateProfile(currentUser.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", user));
    }

    // ── Secured: Change Password ──────────────────────────

    @PutMapping("/password")
    @Operation(summary = "Change password",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {

        authService.changePassword(currentUser.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // ── Secured: Update Status ────────────────────────────

    @PutMapping("/status")
    @Operation(summary = "Update online status (ONLINE / AWAY / DND / INVISIBLE)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateStatusRequest request) {

        UserResponse user = authService.updateStatus(currentUser.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Status updated", user));
    }

    // ── Secured: Record Last Seen (called by WebSocket handler) ──

    @PostMapping("/last-seen")
    @Operation(summary = "Record last seen timestamp — called internally by websocket-handler",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> recordLastSeen(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        authService.recordLastSeen(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Last seen updated", null));
    }

    // ── Secured: Search Users ─────────────────────────────

    @GetMapping("/search")
    @Operation(summary = "Search users by username or full name",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam String q) {

        List<UserResponse> users = authService.searchUsers(q);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    // ── Admin: Get All Users ──────────────────────────────

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — list all users",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(authService.getAllUsers()));
    }

    // ── Admin: Suspend User ───────────────────────────────

    @PutMapping("/admin/users/{userId}/suspend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — suspend a user account",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> suspendUser(@PathVariable Long userId) {
        authService.suspendUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User suspended", null));
    }

    // ── Admin: Reactivate User ────────────────────────────

    @PutMapping("/admin/users/{userId}/reactivate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — reactivate a suspended user",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> reactivateUser(@PathVariable Long userId) {
        authService.reactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User reactivated", null));
    }

    @DeleteMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — permanently delete a user",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        authService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }

    // ── Admin: Promote User ───────────────────────────────

    @PutMapping("/admin/users/{userId}/promote")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — promote a user to platform admin",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> promoteUser(@PathVariable Long userId) {
        authService.promoteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User promoted to platform admin", null));
    }

    // ── Admin: Demote User ────────────────────────────────

    @PutMapping("/admin/users/{userId}/demote")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — demote a platform admin to user",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> demoteUser(@PathVariable Long userId) {
        authService.demoteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User demoted to regular user", null));
    }
}
