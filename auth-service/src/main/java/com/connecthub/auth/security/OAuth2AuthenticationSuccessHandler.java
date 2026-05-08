package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class OAuth2AuthenticationSuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final String frontendUrl;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    // ✅ Use constructor injection instead of @RequiredArgsConstructor
    // so @Value is properly resolved before use
    public OAuth2AuthenticationSuccessHandler(
            JwtService jwtService,
            UserRepository userRepository,
            @Value("${app.frontend-url}") String frontendUrl,
            HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
        this.httpCookieOAuth2AuthorizationRequestRepository = httpCookieOAuth2AuthorizationRequestRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
        clearAuthenticationAttributes(request);

        String email = (String) attributes.get("email");
        if (email == null) {
            email = attributes.get("login") + "@github.placeholder";
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("OAuth2 success but user not found in DB for email: {}", email);
            response.sendRedirect(frontendUrl + "/login?error=user_not_found");
            return;
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        // ✅ frontendUrl is now properly injected — redirects to localhost:3000
        String redirectUrl = frontendUrl + "/oauth2/redirect?token=" + accessToken
                           + "&refreshToken=" + refreshToken;

        log.info("OAuth2 success, redirecting to: {}", redirectUrl);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}