package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@connecthub.com")
                .username("testuser")
                .passwordHash("hashedPass")
                .role(User.UserRole.USER)
                .isActive(true)
                .build();
    }

    @Test
    void loadUserByUsername_Success() {
        when(userRepository.findByEmail("test@connecthub.com")).thenReturn(Optional.of(testUser));

        UserDetails result = userDetailsService.loadUserByUsername("test@connecthub.com");

        assertNotNull(result);
        assertEquals("test@connecthub.com", result.getUsername());
    }

    @Test
    void loadUserByUsername_NotFound_ThrowsException() {
        when(userRepository.findByEmail("unknown@connecthub.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown@connecthub.com"));
    }

    @Test
    void loadUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserDetails result = userDetailsService.loadUserById(1L);

        assertNotNull(result);
        assertEquals("test@connecthub.com", result.getUsername());
    }

    @Test
    void loadUserById_NotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserById(99L));
    }
}