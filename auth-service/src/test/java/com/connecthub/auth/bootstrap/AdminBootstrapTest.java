package com.connecthub.auth.bootstrap;

import com.connecthub.admin.bootstrap.AdminBootstrap;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Environment environment;

    @InjectMocks
    private AdminBootstrap adminBootstrap;

    @BeforeEach
    void setUp() {
        // ✅ FIX: make this lenient to avoid UnnecessaryStubbingException
        lenient().when(passwordEncoder.encode(anyString()))
                 .thenReturn("hashedAdminPassword");
    }

    @Test
    void run_WhenNoAdminExists_CreatesDefaultAdmin() throws Exception {
        when(userRepository.existsByRole(User.UserRole.PLATFORM_ADMIN)).thenReturn(false);
        when(environment.getProperty("admin.default.email", "admin@connecthub.com"))
                .thenReturn("admin@connecthub.com");
        when(environment.getProperty("admin.default.password", "Admin@1234"))
                .thenReturn("Admin@1234");
        when(environment.getProperty("admin.default.username", "superadmin"))
                .thenReturn("superadmin");
        when(environment.getProperty("admin.default.fullName", "Platform Administrator"))
                .thenReturn("Platform Administrator");

        adminBootstrap.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedAdmin = captor.getValue();
        assertEquals("admin@connecthub.com", savedAdmin.getEmail());
        assertEquals("superadmin", savedAdmin.getUsername());
        assertEquals(User.UserRole.PLATFORM_ADMIN, savedAdmin.getRole());
        assertTrue(savedAdmin.getIsActive());
        assertEquals("hashedAdminPassword", savedAdmin.getPasswordHash());
    }

    @Test
    void run_WhenAdminAlreadyExists_DoesNotCreateAdmin() throws Exception {
        when(userRepository.existsByRole(any())).thenReturn(true);

        adminBootstrap.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_WithCustomEnvProperties_UsesCustomValues() throws Exception {
        when(userRepository.existsByRole(User.UserRole.PLATFORM_ADMIN)).thenReturn(false);
        when(environment.getProperty("admin.default.email", "admin@connecthub.com"))
                .thenReturn("custom@myapp.com");
        when(environment.getProperty("admin.default.password", "Admin@1234"))
                .thenReturn("CustomPass@999");
        when(environment.getProperty("admin.default.username", "superadmin"))
                .thenReturn("customadmin");
        when(environment.getProperty("admin.default.fullName", "Platform Administrator"))
                .thenReturn("Custom Admin");

        adminBootstrap.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertEquals("custom@myapp.com", captor.getValue().getEmail());
        assertEquals("customadmin", captor.getValue().getUsername());
        assertEquals("Custom Admin", captor.getValue().getFullName());
    }
}