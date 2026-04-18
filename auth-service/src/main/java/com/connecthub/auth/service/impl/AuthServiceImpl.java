package com.connecthub.auth.service.impl;

import com.connecthub.auth.dto.request.*;
import com.connecthub.auth.dto.response.AuthResponse;
import com.connecthub.auth.dto.response.UserResponse;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.*;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.security.CustomUserDetails;
import com.connecthub.auth.security.JwtService;
import com.connecthub.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // ── Register ───────────────────────────────────────────

    @Override
    public AuthResponse register(RegisterRequest request) {
        // Check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        // Check duplicate username
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .provider(User.AuthProvider.LOCAL)
                .role(User.UserRole.USER)
                .status(User.UserStatus.ONLINE)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("Registered new user: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    // ── Login ──────────────────────────────────────────────

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));

            CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
            User user = userDetails.getUser();

            if (!user.getIsActive()) {
                throw new AccountSuspendedException("Account is suspended. Contact support.");
            }

            log.info("User logged in: {}", user.getEmail());
            return buildAuthResponse(user);

        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }
    }

    // ── Logout ─────────────────────────────────────────────

    @Override
    public void logout(Long userId) {
        User user = findUserById(userId);
        user.setRefreshToken(null);
        user.setLastSeenAt(LocalDateTime.now());
        user.setStatus(User.UserStatus.INVISIBLE);
        userRepository.save(user);
        log.info("User logged out: {}", user.getEmail());
    }

    // ── Refresh Token ──────────────────────────────────────

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtService.isTokenValid(refreshToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired.");
        }

        String email = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new InvalidTokenException("Refresh token does not match. Please login again.");
        }

        log.info("Refreshed tokens for: {}", email);
        return buildAuthResponse(user);
    }

    // ── Get User ───────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserResponse.from(findUserById(id));
    }

    // ── Update Profile ─────────────────────────────────────

    @Override
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserById(userId);

        if (request.getUsername() != null &&
            !request.getUsername().equals(user.getUsername())) {
            userRepository.findByUsernameExcluding(request.getUsername(), userId)
                    .ifPresent(u -> {
                        throw new DuplicateResourceException(
                                "Username already taken: " + request.getUsername());
                    });
            user.setUsername(request.getUsername());
        }

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getBio() != null)      user.setBio(request.getBio());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        user = userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());
        return UserResponse.from(user);
    }

    // ── Change Password ────────────────────────────────────

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserById(userId);

        if (user.getProvider() != User.AuthProvider.LOCAL) {
            throw new BadRequestException("OAuth2 users cannot change password here.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setRefreshToken(null); // Invalidate all sessions
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
    }

    // ── Update Status ──────────────────────────────────────

    @Override
    public UserResponse updateStatus(Long userId, UpdateStatusRequest request) {
        User user = findUserById(userId);

        User.UserStatus newStatus;
        try {
            newStatus = User.UserStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + request.getStatus()
                    + ". Valid values: ONLINE, AWAY, DND, INVISIBLE");
        }

        user.setStatus(newStatus);
        user = userRepository.save(user);
        log.info("Status updated to {} for user: {}", newStatus, user.getEmail());
        return UserResponse.from(user);
    }

    // ── Record Last Seen ───────────────────────────────────

    @Override
    public void recordLastSeen(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastSeenAt(LocalDateTime.now());
            user.setStatus(User.UserStatus.INVISIBLE);
            userRepository.save(user);
        });
    }

    // ── Search Users ───────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BadRequestException("Search keyword cannot be empty.");
        }
        return userRepository.searchByKeyword(keyword.trim())
                .stream()
                .filter(User::getIsActive)
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    // ── Admin Operations ───────────────────────────────────

    @Override
    public void suspendUser(Long userId) {
        User user = findUserById(userId);
        if (user.getRole() == User.UserRole.PLATFORM_ADMIN) {
            throw new BadRequestException("Cannot suspend a platform admin.");
        }
        user.setIsActive(false);
        user.setRefreshToken(null);
        userRepository.save(user);
        log.info("User suspended: {}", user.getEmail());
    }

    @Override
    public void reactivateUser(Long userId) {
        User user = findUserById(userId);
        user.setIsActive(true);
        userRepository.save(user);
        log.info("User reactivated: {}", user.getEmail());
    }

    @Override
    public void deleteUser(Long userId) {
        User user = findUserById(userId);
        userRepository.delete(user);
        log.info("User deleted: id={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    // ── Private helpers ────────────────────────────────────

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private AuthResponse buildAuthResponse(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);

        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // Persist refresh token
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .user(UserResponse.from(user))
                .build();
    }
}
