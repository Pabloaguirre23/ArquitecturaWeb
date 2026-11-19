package ar.edu.unicen.ticketya.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Emite y valida el JWT de usuario (HS256). El gateway es el único que lo firma. */
@Service
public class JwtService {

    public record IssuedToken(String token, Instant expiresAt) {}

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${ticketya.security.jwt.secret}") String secret,
                      @Value("${ticketya.security.jwt.ttl:8h}") Duration ttl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = ttl;
    }

    public IssuedToken issue(DemoUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(user.id().toString())
                .claim("role", user.role())
                .claim("email", user.email())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** Valida firma y expiración. Lanza JwtException si el token no sirve. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}