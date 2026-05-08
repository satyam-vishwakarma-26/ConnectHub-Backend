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
import com.connecthub.auth.service.EmailService;
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
    private final EmailService emailService;
    private final com.connecthub.auth.repository.PasswordResetOtpRepository otpRepository;
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    // ── Registration OTP ───────────────────────────────────

    @Override
    public void requestRegistrationOtp(String email) {
        String cleanEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new DuplicateResourceException("Email already registered: " + cleanEmail);
        }

        otpRepository.invalidateAllOtpsByEmail(cleanEmail);

        String otp = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email(cleanEmail)
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isVerified(false)
                .isUsed(false)
                .build();
        otpRepository.save(otpEntity);
        emailService.sendRegistrationOtpEmail(cleanEmail, otp);
    }

    @Override
    public void verifyRegistrationOtp(String email, String otp) {
        String cleanEmail = email.trim().toLowerCase();
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(cleanEmail)
                .orElseThrow(() -> new BadRequestException("No active OTP found. Please request a new one."));

        if (otpEntity.getIsVerified()) {
            throw new BadRequestException("OTP already verified. Please proceed with registration.");
        }
        if (otpEntity.isMaxAttemptsReached()) {
            otpEntity.setIsUsed(true);
            otpRepository.save(otpEntity);
            throw new BadRequestException("Maximum attempts reached. Request a new OTP.");
        }
        if (otpEntity.isExpired()) {
            otpEntity.setIsUsed(true);
            otpRepository.save(otpEntity);
            throw new BadRequestException("OTP expired. Request a new one.");
        }
        
        otpEntity.setAttempts(otpEntity.getAttempts() + 1);
        if (!otpEntity.getOtp().equals(otp.trim())) {
            otpRepository.save(otpEntity);
            throw new BadRequestException("Invalid OTP.");
        }

        otpEntity.setIsVerified(true);
        otpRepository.save(otpEntity);
    }

    // ── Register ───────────────────────────────────────────

    @Override
    public AuthResponse register(RegisterRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        
        // Ensure OTP was verified before registration
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = otpRepository
                .findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(cleanEmail)
                .orElseThrow(() -> new BadRequestException("Please verify your email first."));
                
        if (!otpEntity.getIsVerified() || otpEntity.isExpired()) {
            throw new BadRequestException("Email not verified or verification expired.");
        }

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

        // Mark OTP as used
        otpEntity.setIsUsed(true);
        otpRepository.save(otpEntity);

        // Send welcome email via RabbitMQ (async, non-blocking)
        emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());

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

        // Notify user via email (async via RabbitMQ)
        emailService.sendAccountSuspendedEmail(user.getEmail(), user.getUsername());
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
        String email = user.getEmail();
        String username = user.getUsername();

        userRepository.delete(user);
        log.info("User deleted: id={}", userId);

        // Notify user via email (async via RabbitMQ) — sent after delete since queue is async
        emailService.sendAccountDeletedEmail(email, username);
    }

    @Override
    public void promoteUser(Long userId) {
        User user = findUserById(userId);
        if (user.getRole() == User.UserRole.PLATFORM_ADMIN) {
            throw new BadRequestException("User is already a platform admin.");
        }
        user.setRole(User.UserRole.PLATFORM_ADMIN);
        userRepository.save(user);
        log.info("User promoted to platform admin: {}", user.getEmail());
    }

    @Override
    public void demoteUser(Long userId) {
        User user = findUserById(userId);
        if (user.getRole() == User.UserRole.USER) {
            throw new BadRequestException("User is already a regular user.");
        }
        user.setRole(User.UserRole.USER);
        userRepository.save(user);
        log.info("User demoted to regular user: {}", user.getEmail());
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
