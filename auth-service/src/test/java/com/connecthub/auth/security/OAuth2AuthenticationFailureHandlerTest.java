package com.connecthub.auth.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationFailureHandlerTest {

    @Mock
    private HttpCookieOAuth2AuthorizationRequestRepository cookieRepo;

    private OAuth2AuthenticationFailureHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationFailureHandler(
                "http://localhost:3000", cookieRepo);
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void onAuthenticationFailure_NoCookie_RedirectsToDefaultLogin() throws Exception {
        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationFailure(request, response, buildException("Access denied"));

        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertTrue(redirectUrl.contains("/login"));
        assertTrue(redirectUrl.contains("error="));
    }

    @Test
    void onAuthenticationFailure_ErrorMessageInUrl() throws Exception {
        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationFailure(request, response,
                buildException("Token expired"));

        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertTrue(redirectUrl.contains("error="));
        assertTrue(redirectUrl.contains("Token"));
    }

    @Test
    void onAuthenticationFailure_WithRedirectUriCookie_UsesIt() throws Exception {
        request.setCookies(new Cookie(
                HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME,
                "http://localhost:3000/callback"));

        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationFailure(request, response,
                buildException("OAuth2 error"));

        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertTrue(redirectUrl.contains("error="));
    }

    @Test
    void onAuthenticationFailure_CleansCookies() throws Exception {
        doNothing().when(cookieRepo)
                .removeAuthorizationRequestCookies(any(), any());

        handler.onAuthenticationFailure(request, response,
                buildException("Some error"));

        verify(cookieRepo).removeAuthorizationRequestCookies(request, response);
    }

    // ── Helper ─────────────────────────────────────────────

    private AuthenticationException buildException(String message) {
        return new AuthenticationException(message) {};
    }
}