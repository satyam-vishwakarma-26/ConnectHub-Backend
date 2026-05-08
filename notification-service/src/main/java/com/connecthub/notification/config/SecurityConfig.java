package com.connecthub.notification.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;

// ── JWT Service ────────────────────────────────────────────
// FIX: Removed duplicate inner AuthUser class.
//      AuthUser now lives in AuthUser.java in this same package.
//      All references here use com.connecthub.notification.config.AuthUser.

@Service
@Slf4j
class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return !claims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            log.warn("JWT invalid: {}", e.getMessage());
            return false;
        }
    }

    public Long   userId(String t) { return claims(t).get("userId", Long.class); }
    public String email(String t)  { return claims(t).getSubject(); }
    public String role(String t)   { return claims(t).get("role", String.class); }

    private Claims claims(String t) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(t).getPayload();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}

// ── JWT Filter ─────────────────────────────────────────────
@Component
@RequiredArgsConstructor
@Slf4j
class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            // Strategy 1: gateway-injected headers
            String uid = req.getHeader("X-Auth-User-Id");
            if (StringUtils.hasText(uid)) {
                try {
                    // FIX: Uses standalone AuthUser from AuthUser.java — no duplicate
                    AuthUser p = new AuthUser(
                            Long.parseLong(uid),
                            req.getHeader("X-Auth-User-Email"),
                            req.getHeader("X-Auth-User-Role"));
                    var auth = new UsernamePasswordAuthenticationToken(
                            p, null, p.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (NumberFormatException ignored) {
                    log.warn("Invalid X-Auth-User-Id header: {}", uid);
                }
            } else {
                // Strategy 2: direct Bearer token (dev without gateway)
                String bearer = req.getHeader("Authorization");
                if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
                    String token = bearer.substring(7);
                    if (jwt.isValid(token)) {
                        String r = jwt.role(token);
                        if (r == null) r = "USER";
                        AuthUser p = new AuthUser(jwt.userId(token), jwt.email(token), r);
                        var auth = new UsernamePasswordAuthenticationToken(
                                p, null, p.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        chain.doFilter(req, res);
    }
}

// ── Security Config ────────────────────────────────────────
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s ->
                    s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers(
                            "/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/swagger-ui/index.html",
                            "/api/swagger-ui/**",
                            "/api/swagger-ui.html",
                            "/api/swagger-ui/index.html",
                            "/actuator/health"
                    ).permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
