package ar.edu.unicen.ticketya.recommendation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI recommendationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketYa - recommendation-service")
                        .version("1.0.0")
                        .description("""
                                Preferencias, historial de compras y recomendaciones de eventos.
                                Se consume a través del gateway en /api/recommendations/**.
                                La identidad llega en los headers X-User-Id, X-User-Role y X-User-Email
                                derivados del JWT por el gateway.
                                """)
                        .license(new License().name("Uso académico - UNICEN")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT emitido por POST /api/auth/login")));
    }
}
