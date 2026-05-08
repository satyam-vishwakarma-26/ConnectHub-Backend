package com.connecthub.auth.controller;

import com.connecthub.auth.dto.request.*;
import com.connecthub.auth.dto.response.ApiResponse;
import com.connecthub.auth.dto.response.AuthResponse;
import com.connecthub.auth.dto.response.UserResponse;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.*;
import com.connecthub.auth.security.CustomUserDetails;
import com.connecthub.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse mockAuthResponse;
    private UserResponse mockUserResponse;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .email("john@connecthub.com")
                .username("johndoe")
                .fullName("John Doe")
                .passwordHash("hashed")
                .role(User.UserRole.USER)
                .status(User.UserStatus.ONLINE)
                .provider(User.AuthProvider.LOCAL)
                .isActive(true)
                .build();

        mockUserDetails = new CustomUserDetails(user);

        mockUserResponse = UserResponse.builder()
                .id(1L)
                .email("john@connecthub.com")
                .username("johndoe")
                .fullName("John Doe")
                .status("ONLINE")
                .role("USER")
                .provider("LOCAL")
                .isActive(true)
                .build();

        mockAuthResponse = AuthResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(mockUserResponse)
                .build();
    }

    // ── POST /auth/register ────────────────────────────────

    @Test
    void register_ValidRequest_Returns201() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("password123");
        request.setFullName("John Doe");

        when(authService.register(any())).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("mock-access-token", response.getBody().getData().getAccessToken());
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@connecthub.com");
        request.setUsername("johndoe");
        request.setPassword("password123");
        request.setFullName("John Doe");

        when(authService.register(any())).thenThrow(
                new DuplicateResourceException("Email already registered"));

        assertThrows(DuplicateResourceException.class,
                () -> authController.register(request));
    }

    // ── POST /auth/login ───────────────────────────────────

    @Test
    void login_ValidCredentials_Returns200() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@connecthub.com");
        request.setPassword("password123");

        when(authService.login(any())).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("mock-access-token", response.getBody().getData().getAccessToken());
    }

    @Test
    void login_InvalidCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@connecthub.com");
        request.setPassword("wrongpass");

        when(authService.login(any())).thenThrow(
                new InvalidCredentialsException("Invalid email or password."));

        assertThrows(InvalidCredentialsException.class,
                () -> authController.login(request));
    }

    @Test
    void login_SuspendedAccount_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@connecthub.com");
        request.setPassword("password123");

        when(authService.login(any())).thenThrow(
                new AccountSuspendedException("Account is suspended."));

        assertThrows(AccountSuspendedException.class,
                () -> authController.login(request));
    }

    // ── POST /auth/refresh ─────────────────────────────────

    @Test
    void refresh_ValidToken_Returns200() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        when(authService.refreshToken(any())).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.refresh(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("mock-access-token", response.getBody().getData().getAccessToken());
    }

    @Test
    void refresh_InvalidToken_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("bad-token");

        when(authService.refreshToken(any())).thenThrow(
                new InvalidTokenException("Token is invalid or expired."));

        assertThrows(InvalidTokenException.class,
                () -> authController.refresh(request));
    }

    // ── POST /auth/logout ──────────────────────────────────

    @Test
    void logout_AuthenticatedUser_Returns200() {
        doNothing().when(authService).logout(1L);

        ResponseEntity<ApiResponse<Void>> response = authController.logout(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(authService).logout(1L);
    }

    @Test
    void logout_UserNotFound_ThrowsException() {
        doThrow(new ResourceNotFoundException("User not found"))
                .when(authService).logout(1L);

        assertThrows(ResourceNotFoundException.class,
                () -> authController.logout(mockUserDetails));
    }

    // ── GET /auth/profile ──────────────────────────────────

    @Test
    void getProfile_AuthenticatedUser_Returns200() {
        when(authService.getUserById(1L)).thenReturn(mockUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
                authController.getProfile(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("john@connecthub.com", response.getBody().getData().getEmail());
    }

    // ── GET /auth/profile/{userId} ─────────────────────────

    @Test
    void getUserById_ValidId_Returns200() {
        when(authService.getUserById(1L)).thenReturn(mockUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response = authController.getUserById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    void getUserById_NotFound_ThrowsException() {
        when(authService.getUserById(99L)).thenThrow(
                new ResourceNotFoundException("User not found with id: 99"));

        assertThrows(ResourceNotFoundException.class,
                () -> authController.getUserById(99L));
    }

    // ── PUT /auth/profile ──────────────────────────────────

    @Test
    void updateProfile_ValidRequest_Returns200() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("John Updated");
        request.setBio("New bio here");

        when(authService.updateProfile(eq(1L), any())).thenReturn(mockUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
                authController.updateProfile(mockUserDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void updateProfile_DuplicateUsername_ThrowsException() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("takenuser");

        when(authService.updateProfile(eq(1L), any())).thenThrow(
                new DuplicateResourceException("Username already taken"));

        assertThrows(DuplicateResourceException.class,
                () -> authController.updateProfile(mockUserDetails, request));
    }

    // ── PUT /auth/password ─────────────────────────────────

    @Test
    void changePassword_ValidRequest_Returns200() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldPass123");
        request.setNewPassword("newPass123");

        doNothing().when(authService).changePassword(eq(1L), any());

        ResponseEntity<ApiResponse<Void>> response =
                authController.changePassword(mockUserDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void changePassword_WrongCurrentPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongPass");
        request.setNewPassword("newPass123");

        doThrow(new InvalidCredentialsException("Current password is incorrect."))
                .when(authService).changePassword(eq(1L), any());

        assertThrows(InvalidCredentialsException.class,
                () -> authController.changePassword(mockUserDetails, request));
    }

    @Test
    void changePassword_OAuth2User_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("pass");
        request.setNewPassword("newpass");

        doThrow(new BadRequestException("OAuth2 users cannot change password"))
                .when(authService).changePassword(eq(1L), any());

        assertThrows(BadRequestException.class,
                () -> authController.changePassword(mockUserDetails, request));
    }

    // ── PUT /auth/status ───────────────────────────────────

    @Test
    void updateStatus_ValidStatus_Returns200() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus("AWAY");

        when(authService.updateStatus(eq(1L), any())).thenReturn(mockUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
                authController.updateStatus(mockUserDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void updateStatus_InvalidStatus_ThrowsException() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus("INVALID");

        when(authService.updateStatus(eq(1L), any())).thenThrow(
                new BadRequestException("Invalid status: INVALID"));

        assertThrows(BadRequestException.class,
                () -> authController.updateStatus(mockUserDetails, request));
    }

    @Test
    void updateStatus_DND_Returns200() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus("DND");

        when(authService.updateStatus(eq(1L), any())).thenReturn(mockUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
                authController.updateStatus(mockUserDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ── POST /auth/last-seen ───────────────────────────────

    @Test
    void recordLastSeen_AuthenticatedUser_Returns200() {
        doNothing().when(authService).recordLastSeen(1L);

        ResponseEntity<ApiResponse<Void>> response =
                authController.recordLastSeen(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(authService).recordLastSeen(1L);
    }

    // ── GET /auth/search ───────────────────────────────────

    @Test
    void searchUsers_ValidQuery_Returns200() {
        when(authService.searchUsers("john")).thenReturn(List.of(mockUserResponse));

        ResponseEntity<ApiResponse<List<UserResponse>>> response =
                authController.searchUsers("john");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("johndoe", response.getBody().getData().get(0).getUsername());
    }

    @Test
    void searchUsers_EmptyQuery_ThrowsException() {
        when(authService.searchUsers("")).thenThrow(
                new BadRequestException("Search keyword cannot be empty."));

        assertThrows(BadRequestException.class,
                () -> authController.searchUsers(""));
    }

    @Test
    void searchUsers_NoResults_ReturnsEmptyList() {
        when(authService.searchUsers("xyz")).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<UserResponse>>> response =
                authController.searchUsers("xyz");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isEmpty());
    }

    // ── Admin: GET /auth/admin/users ───────────────────────

    @Test
    void getAllUsers_Returns200() {
        when(authService.getAllUsers()).thenReturn(List.of(mockUserResponse));

        ResponseEntity<ApiResponse<List<UserResponse>>> response =
                authController.getAllUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    void getAllUsers_EmptyList_Returns200() {
        when(authService.getAllUsers()).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<UserResponse>>> response =
                authController.getAllUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isEmpty());
    }

    // ── Admin: PUT /auth/admin/users/{id}/suspend ──────────

    @Test
    void suspendUser_Returns200() {
        doNothing().when(authService).suspendUser(1L);

        ResponseEntity<ApiResponse<Void>> response = authController.suspendUser(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(authService).suspendUser(1L);
    }

    @Test
    void suspendUser_PlatformAdmin_ThrowsException() {
        doThrow(new BadRequestException("Cannot suspend a platform admin"))
                .when(authService).suspendUser(1L);

        assertThrows(BadRequestException.class, () -> authController.suspendUser(1L));
    }

    // ── Admin: PUT /auth/admin/users/{id}/reactivate ───────

    @Test
    void reactivateUser_Returns200() {
        doNothing().when(authService).reactivateUser(1L);

        ResponseEntity<ApiResponse<Void>> response = authController.reactivateUser(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(authService).reactivateUser(1L);
    }

    // ── Admin: DELETE /auth/admin/users/{id} ───────────────

    @Test
    void deleteUser_Returns200() {
        doNothing().when(authService).deleteUser(1L);

        ResponseEntity<ApiResponse<Void>> response = authController.deleteUser(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(authService).deleteUser(1L);
    }

    @Test
    void deleteUser_NotFound_ThrowsException() {
        doThrow(new ResourceNotFoundException("User not found"))
                .when(authService).deleteUser(99L);

        assertThrows(ResourceNotFoundException.class,
                () -> authController.deleteUser(99L));
    }

    // ── Admin: PUT /auth/admin/users/{id}/promote ──────────

    @Test
    void promoteUser_Returns200() {
        doNothing().when(authService).promoteUser(1L);

        ResponseEntity<ApiResponse<Void>> response = authController.promoteUser(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(authService).promoteUser(1L);
    }

    @Test
    void promoteUser_AlreadyAdmin_ThrowsException() {
        doThrow(new BadRequestException("User is already a platform admin"))
                .when(authService).promoteUser(1L);

        assertThrows(BadRequestException.class, () -> authController.promoteUser(1L));
    }

    // ── Admin: PUT /auth/admin/users/{id}/demote ───────────

    @Test
    void demoteUser_Returns200() {
        doNothing().when(authService).demoteUser(1L);

        ResponseEntity<ApiResponse<Void>> response = authController.demoteUser(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(authService).demoteUser(1L);
    }

    @Test
    void demoteUser_AlreadyUser_ThrowsException() {
        doThrow(new BadRequestException("User is already a regular user"))
                .when(authService).demoteUser(1L);

        assertThrows(BadRequestException.class, () -> authController.demoteUser(1L));
    }
}