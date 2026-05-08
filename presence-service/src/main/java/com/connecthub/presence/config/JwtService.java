package com.connecthub.presence.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service @Slf4j
public class JwtService {
    @Value("${app.jwt.secret}") private String secret;

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return !claims(token).getExpiration().before(new Date());
        } catch (Exception e) { log.warn("JWT invalid: {}", e.getMessage()); return false; }
    }
    public Long   userId(String t) { return claims(t).get("userId", Long.class); }
    public String email(String t)  { return claims(t).getSubject(); }
    public String role(String t)   { return claims(t).get("role", String.class); }
    private Claims claims(String t) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(t).getPayload();
    }
    private SecretKey key() { return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)); }
}
