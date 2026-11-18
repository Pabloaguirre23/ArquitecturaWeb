package ar.edu.unicen.ticketya.gateway.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoUserStore users;
    private final JwtService jwtService;

    public AuthController(DemoUserStore users, JwtService jwtService) {
        this.users = users;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // BCrypt es CPU-bound: no corre en el event loop de WebFlux.
        return Mono.fromCallable(() -> authenticate(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private LoginResponse authenticate(LoginRequest request) {
        DemoUser user = users.findByEmail(request.email())
                .filter(candidate -> users.passwordMatches(request.password(), candidate))
                .orElseThrow(InvalidCredentialsException::new);
        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), user.role(), user.email(), user.nombre(), issued.expiresAt());
    }
}