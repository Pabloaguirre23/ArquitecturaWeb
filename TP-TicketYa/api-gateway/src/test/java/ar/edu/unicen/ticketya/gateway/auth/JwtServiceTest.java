package ar.edu.unicen.ticketya.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "ticketya-test-secret-key-with-more-than-32-bytes";
    private static final DemoUser USER = new DemoUser(
            UUID.fromString("33333333-3333-4333-8333-333333333333"),
            "cliente@ticketya.test", "Cliente Demo", "CLIENT", "hash");

    @Test
    void emiteUnTokenConLosClaimsEsperados() {
        JwtService service = new JwtService(SECRET, Duration.ofHours(8));

        Claims claims = service.parse(service.issue(USER).token());

        assertThat(claims.getSubject()).isEqualTo(USER.id().toString());
        assertThat(claims.get("role", String.class)).isEqualTo("CLIENT");
        assertThat(claims.get("email", String.class)).isEqualTo("cliente@ticketya.test");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void rechazaUnTokenVencido() {
        JwtService service = new JwtService(SECRET, Duration.ofSeconds(-5));
        String token = service.issue(USER).token();

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        JwtService otro = new JwtService("otra-clave-distinta-de-mas-de-32-bytes-123456", Duration.ofHours(1));
        String token = otro.issue(USER).token();
        JwtService service = new JwtService(SECRET, Duration.ofHours(1));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenSinFirmaAlgNone() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + USER.id() + "\",\"role\":\"CLIENT\","
                        + "\"email\":\"cliente@ticketya.test\"}").getBytes(StandardCharsets.UTF_8));
        String sinFirma = header + "." + payload + ".";

        JwtService service = new JwtService(SECRET, Duration.ofHours(1));

        assertThatThrownBy(() -> service.parse(sinFirma)).isInstanceOf(JwtException.class);
    }

    @Test
    void exigeUnSecretoDeAlMenos32Bytes() {
        assertThatThrownBy(() -> new JwtService("corto", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}