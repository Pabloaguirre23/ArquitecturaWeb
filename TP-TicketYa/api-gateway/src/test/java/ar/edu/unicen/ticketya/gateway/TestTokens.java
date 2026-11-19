package ar.edu.unicen.ticketya.gateway;

import ar.edu.unicen.ticketya.gateway.auth.DemoUser;
import ar.edu.unicen.ticketya.gateway.auth.JwtService;
import java.util.UUID;

/** Genera tokens de prueba por rol con el mismo JwtService de la aplicación. */
public final class TestTokens {

    public static final String CLIENT_ID = "33333333-3333-4333-8333-333333333333";
    public static final String ORGANIZER_ID = "22222222-2222-4222-8222-222222222222";
    public static final String ADMIN_ID = "11111111-1111-4111-8111-111111111111";
    public static final String ACCESS_ID = "55555555-5555-4555-8555-555555555555";

    private TestTokens() {}

    /** Devuelve "Bearer <token>". Un rol desconocido sirve para probar que se rechaza. */
    public static String bearer(JwtService jwtService, String role) {
        DemoUser user = switch (role) {
            case "CLIENT" -> demo(CLIENT_ID, "cliente@ticketya.test", role);
            case "ORGANIZER" -> demo(ORGANIZER_ID, "organizador@ticketya.test", role);
            case "ADMIN" -> demo(ADMIN_ID, "admin@ticketya.test", role);
            case "ACCESS_STAFF" -> demo(ACCESS_ID, "acceso@ticketya.test", role);
            default -> demo(CLIENT_ID, "otro@ticketya.test", role);
        };
        return "Bearer " + jwtService.issue(user).token();
    }

    private static DemoUser demo(String id, String email, String role) {
        return new DemoUser(UUID.fromString(id), email, "Test", role, "n/a");
    }
}