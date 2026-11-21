package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.Entrada;
import ar.edu.unicen.ticketya.orders.dominio.EstadoEntrada;
import ar.edu.unicen.ticketya.orders.repositorio.EntradaRepository;
import ar.edu.unicen.ticketya.orders.web.ApiException;
import ar.edu.unicen.ticketya.orders.web.RequestIdentity;
import ar.edu.unicen.ticketya.orders.web.dto.AccessValidationRequest;
import ar.edu.unicen.ticketya.orders.web.dto.AccessValidationResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validación de QR para el personal de acceso (tarea 6.4). El consumo de la
 * entrada es un UPDATE condicional atómico: dos escaneos concurrentes sobre el
 * mismo QR producen exactamente un éxito y un conflicto.
 */
@Service
public class AccessService {

    private static final Set<EstadoEntrada> ESTADOS_NO_VIGENTES = Set.of(
            EstadoEntrada.DEVUELTA, EstadoEntrada.EN_REINTEGRO, EstadoEntrada.EN_DEVOLUCION);

    private final EntradaRepository entradaRepository;
    private final AuditService auditService;
    private final Clock clock;

    public AccessService(EntradaRepository entradaRepository, AuditService auditService, Clock clock) {
        this.entradaRepository = entradaRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public AccessValidationResponse validar(AccessValidationRequest request, RequestIdentity identity) {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        Entrada entrada = entradaRepository.findByQrCode(request.qrCode()).orElse(null);
        if (entrada == null) {
            throw rechazar(identity, null, HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                    "No existe la entrada con QR " + request.qrCode());
        }

        UUID entradaId = entrada.getId();
        if (!request.eventoId().equals(entrada.getEventoId())) {
            throw rechazar(identity, entradaId, HttpStatus.CONFLICT, "TICKET_WRONG_EVENT",
                    "La entrada pertenece a otro evento");
        }

        EstadoEntrada estado = entrada.getEstado();
        if (estado == EstadoEntrada.UTILIZADA || entrada.getUsedAt() != null) {
            throw rechazar(identity, entradaId, HttpStatus.CONFLICT, "TICKET_ALREADY_USED",
                    "La entrada ya fue utilizada");
        }
        if (estado == null || ESTADOS_NO_VIGENTES.contains(estado)) {
            throw rechazar(identity, entradaId, HttpStatus.CONFLICT, "TICKET_NOT_VALID",
                    "La entrada no está vigente: " + estado);
        }

        int filas = entradaRepository.consumir(entradaId, request.eventoId(), ahora, identity.userId());
        if (filas == 0) {
            // Otro escaneo ganó la carrera entre la lectura y el update. Se vuelve
            // a leer solo para distinguir el motivo del conflicto.
            Entrada actual = entradaRepository.findByQrCode(request.qrCode()).orElse(null);
            if (actual == null) {
                throw rechazar(identity, null, HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                        "No existe la entrada con QR " + request.qrCode());
            }
            if (!request.eventoId().equals(actual.getEventoId())) {
                throw rechazar(identity, actual.getId(), HttpStatus.CONFLICT,
                        "TICKET_WRONG_EVENT", "La entrada pertenece a otro evento");
            }
            throw rechazar(identity, actual.getId(), HttpStatus.CONFLICT,
                    "TICKET_ALREADY_USED", "La entrada ya fue utilizada");
        }

        auditService.ingresoValidado(entradaId, identity);
        return AccessValidationResponse.from(entrada, ahora, identity.userId());
    }

    private ApiException rechazar(RequestIdentity identity, UUID entradaId, HttpStatus status,
                                  String code, String detalle) {
        auditService.ingresoRechazado(entradaId, identity, detalle);
        return new ApiException(status, code, detalle);
    }
}
