package com.connecthub.message.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserTest {

    @Test
    void testAuthenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "test@test.com", "USER");

        assertThat(user.getUserId()).isEqualTo(1L);
        assertThat(user.getEmail()).isEqualTo("test@test.com");
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.getUsername()).isEqualTo("test@test.com");
        assertThat(user.getPassword()).isNull();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }
}
