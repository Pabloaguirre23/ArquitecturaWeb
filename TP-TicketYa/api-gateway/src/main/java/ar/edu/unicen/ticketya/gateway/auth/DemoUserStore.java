package ar.edu.unicen.ticketya.gateway.auth;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Usuarios de prueba por rol. Todos usan la contraseña "demo", guardada como hash BCrypt. */
@Component
public class DemoUserStore {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, DemoUser> byEmail;

    public DemoUserStore() {
        String hash = encoder.encode("demo");
        List<DemoUser> users = List.of(
                new DemoUser(UUID.fromString("33333333-3333-4333-8333-333333333333"),
                        "cliente@ticketya.test", "Cliente Demo", "CLIENT", hash),
                new DemoUser(UUID.fromString("22222222-2222-4222-8222-222222222222"),
                        "organizador@ticketya.test", "Organizador Demo", "ORGANIZER", hash),
                new DemoUser(UUID.fromString("11111111-1111-4111-8111-111111111111"),
                        "admin@ticketya.test", "Admin Demo", "ADMIN", hash),
                new DemoUser(UUID.fromString("55555555-5555-4555-8555-555555555555"),
                        "acceso@ticketya.test", "Acceso Demo", "ACCESS_STAFF", hash));
        this.byEmail = users.stream()
                .collect(Collectors.toUnmodifiableMap(DemoUser::email, Function.identity()));
    }

    public Optional<DemoUser> findByEmail(String email) {
        return Optional.ofNullable(byEmail.get(email.trim().toLowerCase(Locale.ROOT)));
    }

    public boolean passwordMatches(String rawPassword, DemoUser user) {
        return encoder.matches(rawPassword, user.passwordHash());
    }
}