package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private HttpCookieOAuth2AuthorizationRequestRepository cookieRepo;
    @Mock private Authentication authentication;

    private OAuth2AuthenticationSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User testUser;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationSuccessHandler(
                jwtService, userRepository,
                "http://localhost:3000", cookieRepo);

        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        testUser = User.builder()
                .id(1L)
                .email("john@gmail.com")
                .username("johndoe")
                .role(User.UserRole.USER)
                .provider(User.AuthProvider.GOOGLE)
                .isActive(true)
                .status(User.UserStatus.ONLINE)
                .build();
    }

    @Test
    void onAuthenticationSuccess_UserFound_RedirectsWithTokens() throws Exception {
        mockOAuth2Principal("john@gmail.com");

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token-abc");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token-xyz");
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationSuccess(request, response, authentication);

        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertTrue(redirectUrl.startsWith("http://localhost:3000/oauth2/redirect"));
        assertTrue(redirectUrl.contains("token=access-token-abc"));
        assertTrue(redirectUrl.contains("refreshToken=refresh-token-xyz"));
    }

    @Test
    void onAuthenticationSuccess_UserNotFound_RedirectsToErrorPage() throws Exception {
        mockOAuth2Principal("unknown@gmail.com");

        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());
        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationSuccess(request, response, authentication);

        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertTrue(redirectUrl.contains("/login?error=user_not_found"));
    }

    @Test
    void onAuthenticationSuccess_SavesRefreshToken() throws Exception {
        mockOAuth2Principal("john@gmail.com");

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("refresh-token", testUser.getRefreshToken());
        verify(userRepository).save(testUser);
    }

    @Test
    void onAuthenticationSuccess_GitHubNullEmail_UsesLoginPlaceholder() throws Exception {
        // GitHub OAuth2 user with no email — uses login@github.placeholder
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "gh-999");
        attrs.put("login", "johngithub");
        attrs.put("name", "John GitHub");
        // no "email" key

        OAuth2UserAuthority authority = new OAuth2UserAuthority(attrs);
        DefaultOAuth2User oAuth2User =
                new DefaultOAuth2User(List.of(authority), attrs, "id");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);

        when(userRepository.findByEmail("johngithub@github.placeholder"))
                .thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(userRepository.save(any())).thenReturn(testUser);
        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userRepository).findByEmail("johngithub@github.placeholder");
        assertNotNull(response.getRedirectedUrl());
    }

    // ── Helper ─────────────────────────────────────────────

    private void mockOAuth2Principal(String email) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "google-123");
        attrs.put("email", email);
        attrs.put("name", "John Doe");

        OAuth2UserAuthority authority = new OAuth2UserAuthority(attrs);
        DefaultOAuth2User oAuth2User =
                new DefaultOAuth2User(List.of(authority), attrs, "sub");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
    }
}