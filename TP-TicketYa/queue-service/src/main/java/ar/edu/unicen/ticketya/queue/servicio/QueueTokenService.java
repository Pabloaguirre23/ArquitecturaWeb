package ar.edu.unicen.ticketya.queue.servicio;

import ar.edu.unicen.ticketya.queue.config.QueueProperties;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Token de fila HS256. Claims: eventId, sub, jti, exp y type=QUEUE.
 * El secreto tiene que medir al menos 32 bytes porque HS256 lo exige.
 */
@Service
public class QueueTokenService {

    static final String TYPE = "QUEUE";

    private final SecretKey key;

    public QueueTokenService(QueueProperties properties) {
        byte[] secret = properties.token().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("QUEUE_TOKEN_SECRET tiene que tener al menos 32 bytes");
        }
        this.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret);
    }

    public String emitir(UUID eventoId, UUID userId, UUID jti, Instant expira) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(jti.toString())
                .claim("eventId", eventoId.toString())
                .claim("type", TYPE)
                .expiration(Date.from(expira))
                .signWith(key)
                .compact();
    }

    public io.jsonwebtoken.Claims leer(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
