package com.connecthub.presence.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component @RequiredArgsConstructor @Slf4j
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // Gateway headers first
            String uid = req.getHeader("X-Auth-User-Id");
            if (StringUtils.hasText(uid)) {
                try {
                    AuthUser p = new AuthUser(Long.parseLong(uid),
                            req.getHeader("X-Auth-User-Email"),
                            req.getHeader("X-Auth-User-Role"));
                    var auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (NumberFormatException ignored) {}
            } else {
                // Direct JWT fallback
                String bearer = req.getHeader("Authorization");
                if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
                    String token = bearer.substring(7);
                    if (jwt.isValid(token)) {
                        String r = jwt.role(token); if (r == null) r = "USER";
                        AuthUser p = new AuthUser(jwt.userId(token), jwt.email(token), r);
                        var auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        chain.doFilter(req, res);
    }
}
