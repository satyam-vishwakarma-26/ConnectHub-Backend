package com.connecthub.auth.service;

import com.connecthub.auth.dto.request.*;
import com.connecthub.auth.dto.response.AuthResponse;
import com.connecthub.auth.dto.response.UserResponse;
import com.connecthub.auth.entity.User;

import java.util.List;

public interface AuthService {

    /**
     * Request an OTP for email verification during registration.
     */
    void requestRegistrationOtp(String email);

    /**
     * Verify the registration OTP.
     */
    void verifyRegistrationOtp(String email, String otp);

    /**
     * Register a new user with email and password.
     */
    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void logout(Long userId);

    AuthResponse refreshToken(RefreshTokenRequest request);

    // ── Profile ───────────────────────────────────────────
    UserResponse getUserById(Long id);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    // ── Status & Presence ─────────────────────────────────
    UserResponse updateStatus(Long userId, UpdateStatusRequest request);

    void recordLastSeen(Long userId);

    // ── Search ────────────────────────────────────────────
    List<UserResponse> searchUsers(String keyword);

    // ── Admin ─────────────────────────────────────────────
    void suspendUser(Long userId);

    void reactivateUser(Long userId);

    void deleteUser(Long userId);

    void promoteUser(Long userId);

    void demoteUser(Long userId);

    List<UserResponse> getAllUsers();
}
