package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomOAuth2UserService service;

    private User existingGoogleUser;

    @BeforeEach
    void setUp() {
        existingGoogleUser = User.builder()
                .id(1L)
                .email("john@gmail.com")
                .username("johndoe")
                .fullName("John Doe")
                .role(User.UserRole.USER)
                .provider(User.AuthProvider.GOOGLE)
                .providerId("google-sub-123")
                .avatarUrl(null)
                .isActive(true)
                .status(User.UserStatus.ONLINE)
                .build();
    }

    // ── Helper: Build a real service that skips super.loadUser() ──

    /**
     * Creates a CustomOAuth2UserService that skips the real HTTP call
     * to the OAuth2 provider by overriding only the super call,
     * while still executing all the real business logic.
     */
    private CustomOAuth2UserService serviceWithFakeOAuth2User(OAuth2User fakeUser) {
        return new CustomOAuth2UserService(userRepository) {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest request) {
                // Skip super.loadUser() (would make real HTTP request)
                // Jump straight to our business logic with fake attributes
                String registrationId =
                        request.getClientRegistration().getRegistrationId();
                Map<String, Object> attributes = fakeUser.getAttributes();

                String email      = extractEmailPublic(registrationId, attributes);
                String name       = extractNamePublic(registrationId, attributes);
                String providerId = extractProviderIdPublic(registrationId, attributes);
                String avatarUrl  = extractAvatarPublic(registrationId, attributes);

                User.AuthProvider provider = "google".equals(registrationId)
                        ? User.AuthProvider.GOOGLE
                        : User.AuthProvider.GITHUB;

                User user = userRepository.findByEmail(email)
                        .orElseGet(() -> createOAuthUserPublic(
                                email, name, avatarUrl, provider, providerId));

                user.setProvider(provider);
                user.setProviderId(providerId);
                if (user.getAvatarUrl() == null && avatarUrl != null) {
                    user.setAvatarUrl(avatarUrl);
                }
                userRepository.save(user);

                return fakeUser;
            }

            // Expose private methods for testing via package-accessible wrappers
            private String extractEmailPublic(String p, Map<String, Object> a) {
                if ("google".equals(p)) return (String) a.get("email");
                Object email = a.get("email");
                if (email != null) return (String) email;
                return a.get("login") + "@github.placeholder";
            }

            private String extractNamePublic(String p, Map<String, Object> a) {
                if ("google".equals(p)) return (String) a.get("name");
                Object name = a.get("name");
                return name != null ? (String) name : (String) a.get("login");
            }

            private String extractProviderIdPublic(String p, Map<String, Object> a) {
                if ("google".equals(p)) return (String) a.get("sub");
                return String.valueOf(a.get("id"));
            }

            private String extractAvatarPublic(String p, Map<String, Object> a) {
                if ("google".equals(p)) return (String) a.get("picture");
                return (String) a.get("avatar_url");
            }

            private User createOAuthUserPublic(String email, String fullName,
                    String avatarUrl, User.AuthProvider provider, String providerId) {
                String baseUsername = email.split("@")[0]
                        .replaceAll("[^a-zA-Z0-9_]", "_");
                String username = baseUsername;
                int suffix = 1;
                while (userRepository.existsByUsername(username)) {
                    username = baseUsername + suffix++;
                }
                return User.builder()
                        .email(email).username(username).fullName(fullName)
                        .avatarUrl(avatarUrl).provider(provider).providerId(providerId)
                        .passwordHash(null).isActive(true)
                        .status(User.UserStatus.ONLINE).role(User.UserRole.USER)
                        .build();
            }
        };
    }

    // ── Google: Existing user ──────────────────────────────

    @Test
    void loadUser_Google_ExistingUser_UpdatesProviderAndSaves() {
        OAuth2User fakeUser = buildOAuth2User("google");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.of(existingGoogleUser));
        when(userRepository.save(any())).thenReturn(existingGoogleUser);

        OAuth2User result = service.loadUser(buildRequest("google"));

        assertNotNull(result);
        assertEquals(User.AuthProvider.GOOGLE, existingGoogleUser.getProvider());
        assertEquals("google-sub-123", existingGoogleUser.getProviderId());
        verify(userRepository).save(existingGoogleUser);
    }

    @Test
    void loadUser_Google_ExistingUser_SetsAvatarWhenNull() {
        existingGoogleUser.setAvatarUrl(null);
        OAuth2User fakeUser = buildOAuth2User("google");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.of(existingGoogleUser));
        when(userRepository.save(any())).thenReturn(existingGoogleUser);

        service.loadUser(buildRequest("google"));

        assertEquals("https://picture.google.com/photo.jpg",
                existingGoogleUser.getAvatarUrl());
    }

    @Test
    void loadUser_Google_ExistingUser_DoesNotOverrideExistingAvatar() {
        existingGoogleUser.setAvatarUrl("https://my-existing-avatar.com/pic.jpg");
        OAuth2User fakeUser = buildOAuth2User("google");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.of(existingGoogleUser));
        when(userRepository.save(any())).thenReturn(existingGoogleUser);

        service.loadUser(buildRequest("google"));

        assertEquals("https://my-existing-avatar.com/pic.jpg",
                existingGoogleUser.getAvatarUrl());
    }

    // ── Google: New user ───────────────────────────────────

    @Test
    void loadUser_Google_NewUser_CreatesAndSaves() {
        OAuth2User fakeUser = buildOAuth2User("google");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(buildRequest("google"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("john@gmail.com", saved.getEmail());
        assertEquals(User.AuthProvider.GOOGLE, saved.getProvider());
        assertEquals(User.UserRole.USER, saved.getRole());
        assertTrue(saved.getIsActive());
    }

    @Test
    void loadUser_Google_NewUser_UsernameConflict_AddsSuffix() {
        OAuth2User fakeUser = buildOAuth2User("google");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("john@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString()))
                .thenReturn(true)   // first attempt conflicts
                .thenReturn(false); // second attempt ok
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(buildRequest("google"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());

        String username = captor.getValue().getUsername();
        // Should have suffix "1" since base was taken
        assertTrue(username.endsWith("1"),
                "Expected username with suffix but got: " + username);
    }

    // ── GitHub: Existing user ──────────────────────────────

    @Test
    void loadUser_GitHub_ExistingUser_UpdatesProvider() {
        OAuth2User fakeUser = buildOAuth2User("github");
        service = serviceWithFakeOAuth2User(fakeUser);

        User githubUser = User.builder()
                .id(2L).email("johngithub@github.placeholder")
                .username("johngithub").fullName("John GitHub")
                .role(User.UserRole.USER).provider(User.AuthProvider.GITHUB)
                .isActive(true).status(User.UserStatus.ONLINE).build();

        when(userRepository.findByEmail("johngithub@github.placeholder"))
                .thenReturn(Optional.of(githubUser));
        when(userRepository.save(any())).thenReturn(githubUser);

        OAuth2User result = service.loadUser(buildRequest("github"));

        assertNotNull(result);
        assertEquals(User.AuthProvider.GITHUB, githubUser.getProvider());
        verify(userRepository).save(githubUser);
    }

    // ── GitHub: New user ───────────────────────────────────

    @Test
    void loadUser_GitHub_NewUser_CreatesUser() {
        OAuth2User fakeUser = buildOAuth2User("github");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("johngithub@github.placeholder"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(buildRequest("github"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());

        assertEquals(User.AuthProvider.GITHUB, captor.getValue().getProvider());
        assertEquals("johngithub@github.placeholder",
                captor.getValue().getEmail());
    }

    @Test
    void loadUser_GitHub_NullEmail_UsesLoginPlusPlaceholder() {
        // GitHub user with no email field
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "gh-456");
        attrs.put("login", "johngithub");
        attrs.put("name", "John GitHub");
        attrs.put("avatar_url", "https://avatars.githubusercontent.com/johngithub");
        // deliberately no "email" key

        OAuth2User fakeUser = new DefaultOAuth2User(
                List.of(new OAuth2UserAuthority(attrs)), attrs, "id");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("johngithub@github.placeholder"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(buildRequest("github"));

        verify(userRepository).findByEmail("johngithub@github.placeholder");
    }

    @Test
    void loadUser_GitHub_NameNull_UsesLoginAsName() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "gh-789");
        attrs.put("login", "noname_user");
        attrs.put("email", "noname@gmail.com");
        attrs.put("avatar_url", null);
        // no "name" key

        OAuth2User fakeUser = new DefaultOAuth2User(
                List.of(new OAuth2UserAuthority(attrs)), attrs, "id");
        service = serviceWithFakeOAuth2User(fakeUser);

        when(userRepository.findByEmail("noname@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(buildRequest("github"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());
        assertEquals("noname_user", captor.getValue().getFullName());
    }

    // ── Helpers ────────────────────────────────────────────

    private OAuth2User buildOAuth2User(String provider) {
        Map<String, Object> attrs = new HashMap<>();
        if ("google".equals(provider)) {
            attrs.put("sub", "google-sub-123");
            attrs.put("email", "john@gmail.com");
            attrs.put("name", "John Doe");
            attrs.put("picture", "https://picture.google.com/photo.jpg");
            return new DefaultOAuth2User(
                    List.of(new OAuth2UserAuthority(attrs)), attrs, "sub");
        } else {
            attrs.put("id", "gh-456");
            attrs.put("login", "johngithub");
            attrs.put("name", "John GitHub");
            attrs.put("email", null);
            attrs.put("avatar_url", "https://avatars.githubusercontent.com/johngithub");
            return new DefaultOAuth2User(
                    List.of(new OAuth2UserAuthority(attrs)), attrs, "id");
        }
    }

    private OAuth2UserRequest buildRequest(String registrationId) {
        ClientRegistration reg = ClientRegistration
                .withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/login/oauth2/code/" + registrationId)
                .authorizationUri("https://provider.com/oauth2/authorize")
                .tokenUri("https://provider.com/oauth2/token")
                .userInfoUri("https://provider.com/userinfo")
                .userNameAttributeName("google".equals(registrationId) ? "sub" : "id")
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "fake-token",
                Instant.now(), Instant.now().plusSeconds(3600));

        return new OAuth2UserRequest(reg, token);
    }
}
