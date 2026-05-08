package com.connecthub.auth.service;

import com.connecthub.auth.dto.request.*;

import com.connecthub.auth.dto.response.AuthResponse;
import com.connecthub.auth.dto.response.UserResponse;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.*;
import com.connecthub.auth.repository.PasswordResetOtpRepository;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.security.CustomUserDetails;
import com.connecthub.auth.security.JwtService;
import com.connecthub.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;
    @Mock private PasswordResetOtpRepository otpRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("john@connecthub.com")
                .username("johndoe")
                .passwordHash("$2a$hashedpassword")
                .fullName("John Doe")
                .role(User.UserRole.USER)
                .provider(User.AuthProvider.LOCAL)
                .status(User.UserStatus.ONLINE)
                .isActive(true)
                .build();
    }

    // ── Register Tests ─────────────────────────────────────

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("password123");
        request.setFullName("John Doe");

        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .isVerified(true)
                .expiryTime(java.time.LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("pass");
        request.setFullName("John");

        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .isVerified(true)
                .expiryTime(java.time.LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));

        when(userRepository.existsByEmail("john@connecthub.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
    }

    @Test
    void register_DuplicateUsername_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("pass");
        request.setFullName("John");

        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("new@connecthub.com")
                .isVerified(true)
                .expiryTime(java.time.LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
    }

    // ── Login Tests ────────────────────────────────────────

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@connecthub.com");
        request.setPassword("password123");

        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(userRepository.save(any())).thenReturn(testUser);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
    }

    @Test
    void login_InvalidCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@connecthub.com");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_SuspendedAccount_ThrowsException() {
        testUser.setIsActive(false);
        LoginRequest request = new LoginRequest();
        request.setEmail("john@connecthub.com");
        request.setPassword("password123");

        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        assertThrows(AccountSuspendedException.class, () -> authService.login(request));
    }

    // ── Logout Tests ───────────────────────────────────────

    @Test
    void logout_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authService.logout(1L);

        assertNull(testUser.getRefreshToken());
        verify(userRepository).save(testUser);
    }

    @Test
    void logout_UserNotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.logout(99L));
    }

    // ── Refresh Token Tests ────────────────────────────────

    @Test
    void refreshToken_Success() {
        testUser.setRefreshToken("valid-refresh-token");
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        when(jwtService.isTokenValid("valid-refresh-token")).thenReturn(true);
        when(jwtService.extractUsername("valid-refresh-token")).thenReturn("john@connecthub.com");
        when(userRepository.findByEmail("john@connecthub.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("new-refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(userRepository.save(any())).thenReturn(testUser);

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
    }

    @Test
    void refreshToken_InvalidToken_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("bad-token");

        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
    }

    @Test
    void refreshToken_TokenMismatch_ThrowsException() {
        testUser.setRefreshToken("stored-token");
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("different-token");

        when(jwtService.isTokenValid("different-token")).thenReturn(true);
        when(jwtService.extractUsername("different-token")).thenReturn("john@connecthub.com");
        when(userRepository.findByEmail("john@connecthub.com")).thenReturn(Optional.of(testUser));

        assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
    }

    // ── Get User Tests ─────────────────────────────────────

    @Test
    void getUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserResponse response = authService.getUserById(1L);

        assertNotNull(response);
        assertEquals("john@connecthub.com", response.getEmail());
    }

    @Test
    void getUserById_NotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.getUserById(99L));
    }

    // ── Update Profile Tests ───────────────────────────────

    @Test
    void updateProfile_Success() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("John Updated");
        request.setBio("New bio");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        UserResponse response = authService.updateProfile(1L, request);

        assertNotNull(response);
        verify(userRepository).save(testUser);
    }

    @Test
    void updateProfile_DuplicateUsername_ThrowsException() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("takenuser");

        User otherUser = User.builder().id(2L).username("takenuser").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsernameExcluding("takenuser", 1L))
                .thenReturn(Optional.of(otherUser));

        assertThrows(DuplicateResourceException.class,
                () -> authService.updateProfile(1L, request));
    }

    // ── Change Password Tests ──────────────────────────────

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldPassword");
        request.setNewPassword("newPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword", testUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newHashedPassword");
        when(userRepository.save(any())).thenReturn(testUser);

        assertDoesNotThrow(() -> authService.changePassword(1L, request));
        verify(userRepository).save(testUser);
    }

    @Test
    void changePassword_WrongCurrentPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongPassword");
        request.setNewPassword("newPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.changePassword(1L, request));
    }

    @Test
    void changePassword_OAuth2User_ThrowsException() {
        testUser.setProvider(User.AuthProvider.GOOGLE);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("pass");
        request.setNewPassword("newpass");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () -> authService.changePassword(1L, request));
    }

    // ── Update Status Tests ────────────────────────────────

    @Test
    void updateStatus_Success() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus("AWAY");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        UserResponse response = authService.updateStatus(1L, request);

        assertNotNull(response);
        assertEquals(User.UserStatus.AWAY, testUser.getStatus());
    }

    @Test
    void updateStatus_InvalidStatus_ThrowsException() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus("INVALID_STATUS");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () -> authService.updateStatus(1L, request));
    }

    // ── Search Tests ───────────────────────────────────────

    @Test
    void searchUsers_Success() {
        when(userRepository.searchByKeyword("john")).thenReturn(List.of(testUser));

        List<UserResponse> results = authService.searchUsers("john");

        assertEquals(1, results.size());
    }

    @Test
    void searchUsers_EmptyKeyword_ThrowsException() {
        assertThrows(BadRequestException.class, () -> authService.searchUsers(""));
    }

    @Test
    void searchUsers_NullKeyword_ThrowsException() {
        assertThrows(BadRequestException.class, () -> authService.searchUsers(null));
    }

    // ── Admin Tests ────────────────────────────────────────

    @Test
    void suspendUser_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authService.suspendUser(1L);

        assertFalse(testUser.getIsActive());
    }

    @Test
    void suspendUser_PlatformAdmin_ThrowsException() {
        testUser.setRole(User.UserRole.PLATFORM_ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () -> authService.suspendUser(1L));
    }

    @Test
    void reactivateUser_Success() {
        testUser.setIsActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authService.reactivateUser(1L);

        assertTrue(testUser.getIsActive());
    }

    @Test
    void deleteUser_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        doNothing().when(userRepository).delete(testUser);

        authService.deleteUser(1L);

        verify(userRepository).delete(testUser);
    }

    @Test
    void promoteUser_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authService.promoteUser(1L);

        assertEquals(User.UserRole.PLATFORM_ADMIN, testUser.getRole());
    }

    @Test
    void promoteUser_AlreadyAdmin_ThrowsException() {
        testUser.setRole(User.UserRole.PLATFORM_ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () -> authService.promoteUser(1L));
    }

    @Test
    void demoteUser_Success() {
        testUser.setRole(User.UserRole.PLATFORM_ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authService.demoteUser(1L);

        assertEquals(User.UserRole.USER, testUser.getRole());
    }

    @Test
    void demoteUser_AlreadyUser_ThrowsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(BadRequestException.class, () -> authService.demoteUser(1L));
    }

    @Test
    void getAllUsers_Success() {
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        List<UserResponse> users = authService.getAllUsers();

        assertEquals(1, users.size());
    }

    @Test
    void recordLastSeen_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authService.recordLastSeen(1L);

        assertEquals(User.UserStatus.INVISIBLE, testUser.getStatus());
        assertNotNull(testUser.getLastSeenAt());
    }

    // ── Registration OTP Tests ────────────────────────────

    @Test
    void requestRegistrationOtp_Success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(otpRepository.save(any())).thenReturn(null);
        doNothing().when(emailService).sendRegistrationOtpEmail(anyString(), anyString());

        assertDoesNotThrow(() -> authService.requestRegistrationOtp("new@connecthub.com"));

        verify(otpRepository).invalidateAllOtpsByEmail(anyString());
        verify(emailService).sendRegistrationOtpEmail(anyString(), anyString());
    }

    @Test
    void requestRegistrationOtp_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmail("existing@connecthub.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> authService.requestRegistrationOtp("existing@connecthub.com"));
    }

    @Test
    void verifyRegistrationOtp_Success() {
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .otp("123456")
                .attempts(0)
                .isVerified(false)
                .expiryTime(java.time.LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));
        when(otpRepository.save(any())).thenReturn(otpEntity);

        assertDoesNotThrow(() -> authService.verifyRegistrationOtp("john@connecthub.com", "123456"));
    }

    @Test
    void verifyRegistrationOtp_NoOtpFound_ThrowsException() {
        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> authService.verifyRegistrationOtp("john@connecthub.com", "123456"));
    }

    @Test
    void verifyRegistrationOtp_AlreadyVerified_ThrowsException() {
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .isVerified(true)
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));

        assertThrows(BadRequestException.class,
                () -> authService.verifyRegistrationOtp("john@connecthub.com", "123456"));
    }

    @Test
    void verifyRegistrationOtp_Expired_ThrowsException() {
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .isVerified(false)
                .isUsed(false)
                .expiryTime(java.time.LocalDateTime.now().minusMinutes(1))
                .build();

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));
        when(otpRepository.save(any())).thenReturn(otpEntity);

        assertThrows(BadRequestException.class,
                () -> authService.verifyRegistrationOtp("john@connecthub.com", "123456"));
    }

    @Test
    void verifyRegistrationOtp_InvalidOtp_ThrowsException() {
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .otp("123456")
                .attempts(0)
                .isVerified(false)
                .expiryTime(java.time.LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));
        when(otpRepository.save(any())).thenReturn(otpEntity);

        assertThrows(BadRequestException.class,
                () -> authService.verifyRegistrationOtp("john@connecthub.com", "wrong"));
    }

    @Test
    void verifyRegistrationOtp_MaxAttemptsReached_ThrowsException() {
        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .otp("123456")
                .attempts(3)
                .isVerified(false)
                .expiryTime(java.time.LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));
        when(otpRepository.save(any())).thenReturn(otpEntity);

        assertThrows(BadRequestException.class,
                () -> authService.verifyRegistrationOtp("john@connecthub.com", "123456"));
    }

    @Test
    void register_NoVerifiedOtp_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("password123");
        request.setFullName("John Doe");

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> authService.register(request));
    }

    @Test
    void register_ExpiredOtp_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("password123");
        request.setFullName("John Doe");

        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .isVerified(true)
                .expiryTime(java.time.LocalDateTime.now().minusMinutes(5))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));

        assertThrows(BadRequestException.class, () -> authService.register(request));
    }

    @Test
    void register_UnverifiedOtp_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("password123");
        request.setFullName("John Doe");

        com.connecthub.auth.entity.PasswordResetOtp otpEntity = com.connecthub.auth.entity.PasswordResetOtp.builder()
                .email("john@connecthub.com")
                .isVerified(false)
                .expiryTime(java.time.LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .build();

        when(otpRepository.findTopByEmailAndIsUsedFalseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.of(otpEntity));

        assertThrows(BadRequestException.class, () -> authService.register(request));
    }
}