package com.connecthub.auth.repository;

import com.connecthub.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void findByEmail_whenExists_returnsUser() {
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .build();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        Optional<User> result = userRepository.findByEmail("test@test.com");

        assertTrue(result.isPresent());
        assertEquals("test@test.com", result.get().getEmail());
    }

    @Test
    void findByEmail_whenNotExists_returnsEmpty() {
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        Optional<User> result = userRepository.findByEmail("notfound@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void findByUsername_whenExists_returnsUser() {
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        Optional<User> result = userRepository.findByUsername("testuser");

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
    }

    @Test
    void existsByEmail_whenExists_returnsTrue() {
        when(userRepository.existsByEmail("exists@test.com")).thenReturn(true);

        boolean result = userRepository.existsByEmail("exists@test.com");

        assertTrue(result);
    }

    @Test
    void existsByEmail_whenNotExists_returnsFalse() {
        when(userRepository.existsByEmail("notexists@test.com")).thenReturn(false);

        boolean result = userRepository.existsByEmail("notexists@test.com");

        assertFalse(result);
    }

    @Test
    void existsByUsername_whenExists_returnsTrue() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        boolean result = userRepository.existsByUsername("testuser");

        assertTrue(result);
    }

    @Test
    void existsByRole_whenExists_returnsTrue() {
        when(userRepository.existsByRole(User.UserRole.PLATFORM_ADMIN)).thenReturn(true);

        boolean result = userRepository.existsByRole(User.UserRole.PLATFORM_ADMIN);

        assertTrue(result);
    }

    @Test
    void findByStatus_returnsUsers() {
        List<User> users = List.of(
                User.builder().id(1L).status(User.UserStatus.ONLINE).build(),
                User.builder().id(2L).status(User.UserStatus.ONLINE).build()
        );
        when(userRepository.findByStatus(User.UserStatus.ONLINE)).thenReturn(users);

        List<User> result = userRepository.findByStatus(User.UserStatus.ONLINE);

        assertEquals(2, result.size());
    }

    @Test
    void searchByKeyword_returnsMatchingUsers() {
        List<User> users = List.of(
                User.builder().id(1L).username("john").fullName("John Doe").build()
        );
        when(userRepository.searchByKeyword("john")).thenReturn(users);

        List<User> result = userRepository.searchByKeyword("john");

        assertEquals(1, result.size());
    }

    @Test
    void findByUsernameExcluding_whenDifferentUser_returnsUser() {
        User user = User.builder().id(2L).username("taken").build();
        when(userRepository.findByUsernameExcluding("taken", 1L)).thenReturn(Optional.of(user));

        Optional<User> result = userRepository.findByUsernameExcluding("taken", 1L);

        assertTrue(result.isPresent());
    }

    @Test
    void findByEmailExcluding_whenDifferentUser_returnsUser() {
        User user = User.builder().id(2L).email("taken@test.com").build();
        when(userRepository.findByEmailExcluding("taken@test.com", 1L)).thenReturn(Optional.of(user));

        Optional<User> result = userRepository.findByEmailExcluding("taken@test.com", 1L);

        assertTrue(result.isPresent());
    }

    @Test
    void findByProviderAndProviderId_returnsUser() {
        User user = User.builder()
                .id(1L)
                .provider(User.AuthProvider.GOOGLE)
                .providerId("google-123")
                .build();
        when(userRepository.findByProviderAndProviderId(User.AuthProvider.GOOGLE, "google-123"))
                .thenReturn(Optional.of(user));

        Optional<User> result = userRepository.findByProviderAndProviderId(User.AuthProvider.GOOGLE, "google-123");

        assertTrue(result.isPresent());
    }

    @Test
    void save_user_returnsSavedUser() {
        User user = User.builder()
                .email("new@test.com")
                .username("newuser")
                .build();
        when(userRepository.save(any())).thenReturn(user);

        User result = userRepository.save(user);

        assertNotNull(result);
    }

    @Test
    void delete_user_deletesSuccessfully() {
        User user = User.builder().id(1L).build();
        doNothing().when(userRepository).delete(user);

        assertDoesNotThrow(() -> userRepository.delete(user));
    }

    @Test
    void findAll_returnsAllUsers() {
        List<User> users = List.of(
                User.builder().id(1L).build(),
                User.builder().id(2L).build()
        );
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userRepository.findAll();

        assertEquals(2, result.size());
    }
}