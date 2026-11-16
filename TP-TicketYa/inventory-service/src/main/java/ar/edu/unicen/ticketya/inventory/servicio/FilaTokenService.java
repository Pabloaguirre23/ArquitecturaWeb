package ar.edu.unicen.ticketya.inventory.servicio;

import ar.edu.unicen.ticketya.inventory.config.QueueTokenProperties;
import ar.edu.unicen.ticketya.inventory.web.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Valida el JWT de fila en local, con el mismo secreto HS256 que queue-service.
 * No llama a queue. Un token sigue valiendo hasta su exp; no hay lista de revocación.
 */
@Service
public class FilaTokenService {

    static final String TYPE = "QUEUE";

    private final SecretKey key;

    public FilaTokenService(QueueTokenProperties properties) {
        byte[] secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("QUEUE_TOKEN_SECRET tiene que tener al menos 32 bytes");
        }
        this.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret);
    }

    public TokenValido validar(String token, UUID eventoId, UUID clienteId) {
        if (token == null || token.isBlank()) {
            throw ApiException.forbidden("QUEUE_REQUIRED", "Este evento exige un token de fila vigente");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!TYPE.equals(claims.get("type", String.class))
                    || !eventoId.toString().equals(claims.get("eventId", String.class))
                    || !clienteId.toString().equals(claims.getSubject())) {
                throw ApiException.forbidden("QUEUE_TOKEN_INVALID",
                        "El token de fila no corresponde a este cliente y evento");
            }
            return new TokenValido(UUID.fromString(claims.getId()));
        } catch (ApiException ex) {
            throw ex;
        } catch (ExpiredJwtException ex) {
            throw ApiException.forbidden("QUEUE_TOKEN_INVALID", "El token de fila está vencido");
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.forbidden("QUEUE_TOKEN_INVALID", "El token de fila no es válido");
        }
    }

    public record TokenValido(UUID jti) {
    }
}
