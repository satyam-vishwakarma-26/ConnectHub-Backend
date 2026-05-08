package com.connecthub.auth.security;

import com.connecthub.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    private CustomUserDetails activeUserDetails;
    private CustomUserDetails inactiveUserDetails;
    private CustomUserDetails adminUserDetails;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder()
                .id(1L).email("john@connecthub.com")
                .username("johndoe").passwordHash("hashed")
                .role(User.UserRole.USER)
                .status(User.UserStatus.ONLINE)
                .isActive(true).build();

        User inactiveUser = User.builder()
                .id(2L).email("inactive@connecthub.com")
                .username("inactive").passwordHash("hashed")
                .role(User.UserRole.USER)
                .status(User.UserStatus.INVISIBLE)
                .isActive(false).build();

        User adminUser = User.builder()
                .id(3L).email("admin@connecthub.com")
                .username("admin").passwordHash("hashed")
                .role(User.UserRole.PLATFORM_ADMIN)
                .status(User.UserStatus.ONLINE)
                .isActive(true).build();

        activeUserDetails   = new CustomUserDetails(activeUser);
        inactiveUserDetails = new CustomUserDetails(inactiveUser);
        adminUserDetails    = new CustomUserDetails(adminUser);
    }

    @Test
    void getUserId_ReturnsCorrectId() {
        assertEquals(1L, activeUserDetails.getUserId());
    }

    @Test
    void getUsername_ReturnsEmail() {
        assertEquals("john@connecthub.com", activeUserDetails.getUsername());
    }

    @Test
    void getPassword_ReturnsPasswordHash() {
        assertEquals("hashed", activeUserDetails.getPassword());
    }

    @Test
    void getAuthorities_ReturnsRoleUser() {
        Collection<? extends GrantedAuthority> authorities =
                activeUserDetails.getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals("ROLE_USER",
                authorities.iterator().next().getAuthority());
    }

    @Test
    void getAuthorities_ReturnsRolePlatformAdmin() {
        Collection<? extends GrantedAuthority> authorities =
                adminUserDetails.getAuthorities();
        assertEquals("ROLE_PLATFORM_ADMIN",
                authorities.iterator().next().getAuthority());
    }

    @Test
    void isAccountNonExpired_AlwaysTrue() {
        assertTrue(activeUserDetails.isAccountNonExpired());
        assertTrue(inactiveUserDetails.isAccountNonExpired());
    }

    @Test
    void isAccountNonLocked_TrueForActiveUser() {
        assertTrue(activeUserDetails.isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_FalseForInactiveUser() {
        assertFalse(inactiveUserDetails.isAccountNonLocked());
    }

    @Test
    void isCredentialsNonExpired_AlwaysTrue() {
        assertTrue(activeUserDetails.isCredentialsNonExpired());
    }

    @Test
    void isEnabled_TrueForActiveUser() {
        assertTrue(activeUserDetails.isEnabled());
    }

    @Test
    void isEnabled_FalseForInactiveUser() {
        assertFalse(inactiveUserDetails.isEnabled());
    }

    @Test
    void getUser_ReturnsUnderlyingUser() {
        assertNotNull(activeUserDetails.getUser());
        assertEquals("john@connecthub.com", activeUserDetails.getUser().getEmail());
    }
}