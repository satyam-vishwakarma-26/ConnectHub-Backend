package com.connecthub.auth.util;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CookieUtilsTest {

    // ── getCookie ──────────────────────────────────────────

    @Test
    void getCookie_WhenCookieExists_ReturnsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("token", "abc123"));

        Optional<Cookie> result = CookieUtils.getCookie(request, "token");

        assertTrue(result.isPresent());
        assertEquals("abc123", result.get().getValue());
    }

    @Test
    void getCookie_WhenCookieNotFound_ReturnsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "value"));

        Optional<Cookie> result = CookieUtils.getCookie(request, "token");

        assertFalse(result.isPresent());
    }

    @Test
    void getCookie_WhenNoCookies_ReturnsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Optional<Cookie> result = CookieUtils.getCookie(request, "token");

        assertFalse(result.isPresent());
    }

    @Test
    void getCookie_MultipleCookies_ReturnsCorrectOne() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("first", "1"),
                new Cookie("token", "mytoken"),
                new Cookie("last", "3")
        );

        Optional<Cookie> result = CookieUtils.getCookie(request, "token");

        assertTrue(result.isPresent());
        assertEquals("mytoken", result.get().getValue());
    }

    // ── addCookie ──────────────────────────────────────────

    @Test
    void addCookie_AddsCorrectCookieToResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.addCookie(response, "mytoken", "myvalue", 3600);

        Cookie cookie = response.getCookie("mytoken");
        assertNotNull(cookie);
        assertEquals("myvalue", cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(3600, cookie.getMaxAge());
    }

    // ── deleteCookie ───────────────────────────────────────

    @Test
    void deleteCookie_WhenCookieExists_SetsMaxAgeZero() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("token", "abc123"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.deleteCookie(request, response, "token");

        Cookie deleted = response.getCookie("token");
        assertNotNull(deleted);
        assertEquals(0, deleted.getMaxAge());
        assertEquals("", deleted.getValue());
    }

    @Test
    void deleteCookie_WhenCookieDoesNotExist_DoesNothing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() ->
                CookieUtils.deleteCookie(request, response, "token"));
    }

    @Test
    void deleteCookie_WhenNoCookies_DoesNothing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() ->
                CookieUtils.deleteCookie(request, response, "token"));
    }

    // ── serialize / deserialize ────────────────────────────

    @Test
    void serialize_AndDeserialize_RoundTrip() {
        String original = "hello-world";

        String serialized = CookieUtils.serialize(original);
        assertNotNull(serialized);
        assertFalse(serialized.isEmpty());

        Cookie cookie = new Cookie("test", serialized);
        String deserialized = CookieUtils.deserialize(cookie, String.class);

        assertEquals(original, deserialized);
    }
}