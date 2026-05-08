package com.connecthub.auth.security;

import com.connecthub.auth.util.CookieUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.junit.jupiter.api.Assertions.*;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private HttpCookieOAuth2AuthorizationRequestRepository repository;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        repository = new HttpCookieOAuth2AuthorizationRequestRepository();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    // ── saveAuthorizationRequest ───────────────────────────

    @Test
    void saveAuthorizationRequest_SavesCookies() {
        OAuth2AuthorizationRequest authRequest = buildAuthRequest();

        repository.saveAuthorizationRequest(authRequest, request, response);

        Cookie saved = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository
                        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        assertNotNull(saved);
    }

    @Test
    void saveAuthorizationRequest_WithRedirectUri_SavesRedirectCookie() {
        OAuth2AuthorizationRequest authRequest = buildAuthRequest();
        request.setParameter(
                HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME,
                "http://localhost:3000/callback");

        repository.saveAuthorizationRequest(authRequest, request, response);

        Cookie redirectCookie = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME);
        assertNotNull(redirectCookie);
        assertEquals("http://localhost:3000/callback", redirectCookie.getValue());
    }

    @Test
    void saveAuthorizationRequest_WhenNull_DeletesCookies() {
        // First save a cookie
        Cookie existing = new Cookie(
                HttpCookieOAuth2AuthorizationRequestRepository
                        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "somevalue");
        request.setCookies(existing);

        // Now save null — should delete
        repository.saveAuthorizationRequest(null, request, response);

        Cookie deleted = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository
                        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if (deleted != null) {
            assertEquals(0, deleted.getMaxAge());
        }
    }

    // ── loadAuthorizationRequest ───────────────────────────

    @Test
    void loadAuthorizationRequest_WhenNoCookie_ReturnsNull() {
        OAuth2AuthorizationRequest result = repository.loadAuthorizationRequest(request);
        assertNull(result);
    }

    @Test
    void loadAuthorizationRequest_WhenCookieExists_ReturnsRequest() {
        OAuth2AuthorizationRequest authRequest = buildAuthRequest();
        String serialized = CookieUtils.serialize(authRequest);
        request.setCookies(new Cookie(
                HttpCookieOAuth2AuthorizationRequestRepository
                        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                serialized));

        OAuth2AuthorizationRequest result = repository.loadAuthorizationRequest(request);

        assertNotNull(result);
        assertEquals(authRequest.getAuthorizationUri(), result.getAuthorizationUri());
    }

    // ── removeAuthorizationRequest ─────────────────────────

    @Test
    void removeAuthorizationRequest_ReturnsAndDeletesCookie() {
        OAuth2AuthorizationRequest authRequest = buildAuthRequest();
        String serialized = CookieUtils.serialize(authRequest);
        request.setCookies(new Cookie(
                HttpCookieOAuth2AuthorizationRequestRepository
                        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                serialized));

        OAuth2AuthorizationRequest result =
                repository.removeAuthorizationRequest(request, response);

        assertNotNull(result);
    }

    @Test
    void removeAuthorizationRequest_WhenNoCookie_ReturnsNull() {
        OAuth2AuthorizationRequest result =
                repository.removeAuthorizationRequest(request, response);
        assertNull(result);
    }

    // ── removeAuthorizationRequestCookies ──────────────────

    @Test
    void removeAuthorizationRequestCookies_DeletesBothCookies() {
        request.setCookies(
                new Cookie(HttpCookieOAuth2AuthorizationRequestRepository
                        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "val1"),
                new Cookie(HttpCookieOAuth2AuthorizationRequestRepository
                        .REDIRECT_URI_PARAM_COOKIE_NAME, "val2")
        );

        repository.removeAuthorizationRequestCookies(request, response);

        Cookie auth = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository
                        .OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        Cookie redirect = response.getCookie(
                HttpCookieOAuth2AuthorizationRequestRepository
                        .REDIRECT_URI_PARAM_COOKIE_NAME);

        if (auth != null) assertEquals(0, auth.getMaxAge());
        if (redirect != null) assertEquals(0, redirect.getMaxAge());
    }

    // ── Helper ─────────────────────────────────────────────

    private OAuth2AuthorizationRequest buildAuthRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .clientId("test-client")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scope("email", "profile")
                .state("test-state")
                .build();
    }
}