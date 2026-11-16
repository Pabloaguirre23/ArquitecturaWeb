package ar.edu.unicen.ticketya.recommendation.web;

import ar.edu.unicen.ticketya.recommendation.servicio.RecommendationService;
import ar.edu.unicen.ticketya.recommendation.servicio.SimilarEventsService;
import ar.edu.unicen.ticketya.recommendation.web.dto.EventosSimilaresResponse;
import ar.edu.unicen.ticketya.recommendation.web.dto.RecomendacionesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recomendaciones", description = "Sugerencias personalizadas del cliente")
@SecurityRequirement(name = "X-User-Id")
@SecurityRequirement(name = "X-User-Role")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final SimilarEventsService similarEventsService;

    public RecommendationController(RecommendationService recommendationService,
                                    SimilarEventsService similarEventsService) {
        this.recommendationService = recommendationService;
        this.similarEventsService = similarEventsService;
    }

    @GetMapping
    @Operation(summary = "Mis recomendaciones",
            description = "Devuelve eventos publicados y futuros ordenados por afinidad, popularidad "
                    + "y co-compra. Cada sugerencia incluye los motivos. Si catalog no responde, "
                    + "usa el snapshot local; si no hay candidatos, devuelve una lista vacía.")
    @ApiResponse(responseCode = "200", description = "Recomendaciones del cliente")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT (FORBIDDEN)")
    public RecomendacionesResponse recomendar(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("CLIENT");
        return recommendationService.recomendar(identity.userId());
    }

    @GetMapping("/events/{eventId}/similar")
    @Operation(summary = "Eventos similares",
            description = "Devuelve eventos comprados por otros clientes que compraron el mismo "
                    + "evento, ordenados por cantidad de compradores en común. Excluye el evento "
                    + "consultado, los que el cliente ya compró, los cancelados y los pasados. "
                    + "Sin datos suficientes devuelve una lista vacía.")
    @ApiResponse(responseCode = "200", description = "Eventos similares por co-compra")
    @ApiResponse(responseCode = "400", description = "eventId inválido (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Falta identidad (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT (FORBIDDEN)")
    public EventosSimilaresResponse similares(
            @PathVariable UUID eventId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        RequestIdentity identity = RequestIdentity.require(userId, userRole);
        identity.requireRole("CLIENT");
        return similarEventsService.similares(identity.userId(), eventId);
    }
}
