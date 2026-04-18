package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String registrationId = request.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email      = extractEmail(registrationId, attributes);
        String name       = extractName(registrationId, attributes);
        String providerId = extractProviderId(registrationId, attributes);
        String avatarUrl  = extractAvatar(registrationId, attributes);

        User.AuthProvider provider = "google".equals(registrationId)
                ? User.AuthProvider.GOOGLE
                : User.AuthProvider.GITHUB;

        // Upsert user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createOAuthUser(email, name, avatarUrl, provider, providerId));

        // Update provider details on every login
        user.setProvider(provider);
        user.setProviderId(providerId);
        if (user.getAvatarUrl() == null && avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        userRepository.save(user);

        log.info("OAuth2 login: provider={} email={}", registrationId, email);
        return oAuth2User;
    }

    // ── Extraction helpers ─────────────────────────────────

    private String extractEmail(String provider, Map<String, Object> attrs) {
        if ("google".equals(provider)) {
            return (String) attrs.get("email");
        }
        // GitHub
        Object email = attrs.get("email");
        if (email != null) return (String) email;
        return attrs.get("login") + "@github.placeholder";
    }

    private String extractName(String provider, Map<String, Object> attrs) {
        if ("google".equals(provider)) return (String) attrs.get("name");
        Object name = attrs.get("name");
        return name != null ? (String) name : (String) attrs.get("login");
    }

    private String extractProviderId(String provider, Map<String, Object> attrs) {
        if ("google".equals(provider)) return (String) attrs.get("sub");
        return String.valueOf(attrs.get("id"));
    }

    private String extractAvatar(String provider, Map<String, Object> attrs) {
        if ("google".equals(provider)) return (String) attrs.get("picture");
        return (String) attrs.get("avatar_url");
    }

    private User createOAuthUser(String email, String fullName, String avatarUrl,
                                  User.AuthProvider provider, String providerId) {
        String baseUsername = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9_]", "_");

        // Ensure username uniqueness
        String username = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + suffix++;
        }

        return User.builder()
                .email(email)
                .username(username)
                .fullName(fullName)
                .avatarUrl(avatarUrl)
                .provider(provider)
                .providerId(providerId)
                .passwordHash(null)
                .isActive(true)
                .status(User.UserStatus.ONLINE)
                .role(User.UserRole.USER)
                .build();
    }
}
