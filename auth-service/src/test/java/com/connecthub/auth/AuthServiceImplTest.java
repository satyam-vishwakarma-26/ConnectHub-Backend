package com.connecthub.auth;

import com.connecthub.auth.dto.request.LoginRequest;
import com.connecthub.auth.dto.request.RegisterRequest;
import com.connecthub.auth.dto.response.AuthResponse;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.DuplicateResourceException;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.security.CustomUserDetails;
import com.connecthub.auth.security.JwtService;
import com.connecthub.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Tests")
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("alice@example.com")
                .username("alice")
                .passwordHash("$2a$12$hashedpassword")
                .fullName("Alice Smith")
                .provider(User.AuthProvider.LOCAL)
                .role(User.UserRole.USER)
                .status(User.UserStatus.ONLINE)
                .isActive(true)
                .build();
    }

    // ── Register ───────────────────────────────────────────

    @Test
    @DisplayName("register() — happy path creates user and returns tokens")
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@example.com");
        req.setUsername("alice");
        req.setPassword("password123");
        req.setFullName("Alice Smith");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(req.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(req.getPassword())).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        AuthResponse response = authService.register(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUser().getEmail()).isEqualTo("alice@example.com");
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("register() — throws DuplicateResourceException on duplicate email")
    void register_duplicateEmail_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@example.com");
        req.setUsername("alice");
        req.setPassword("password123");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    @DisplayName("register() — throws DuplicateResourceException on duplicate username")
    void register_duplicateUsername_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@example.com");
        req.setUsername("alice");
        req.setPassword("password123");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(req.getUsername())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Username already taken");
    }

    // ── Login ──────────────────────────────────────────────

    @Test
    @DisplayName("login() — happy path returns tokens")
    void login_success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);
        when(userRepository.save(any())).thenReturn(testUser);

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUser().getUsername()).isEqualTo("alice");
    }

    // ── Search ─────────────────────────────────────────────

    @Test
    @DisplayName("searchUsers() — returns matching active users")
    void searchUsers_returnsResults() {
        when(userRepository.searchByKeyword("alice")).thenReturn(java.util.List.of(testUser));

        var results = authService.searchUsers("alice");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("searchUsers() — blank keyword throws BadRequestException")
    void searchUsers_blankKeyword_throws() {
        assertThatThrownBy(() -> authService.searchUsers("  "))
                .isInstanceOf(com.connecthub.auth.exception.BadRequestException.class);
    }

    // ── getUserById ────────────────────────────────────────

    @Test
    @DisplayName("getUserById() — returns user response for valid id")
    void getUserById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        var result = authService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("getUserById() — throws ResourceNotFoundException for unknown id")
    void getUserById_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getUserById(99L))
                .isInstanceOf(com.connecthub.auth.exception.ResourceNotFoundException.class);
    }
}
