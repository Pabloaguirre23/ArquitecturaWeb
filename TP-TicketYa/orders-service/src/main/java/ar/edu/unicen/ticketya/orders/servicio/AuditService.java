package ar.edu.unicen.ticketya.orders.servicio;

import ar.edu.unicen.ticketya.orders.dominio.AuditLog;
import ar.edu.unicen.ticketya.orders.dominio.ResultadoAuditoria;
import ar.edu.unicen.ticketya.orders.eventos.CorrelationContext;
import ar.edu.unicen.ticketya.orders.repositorio.AuditLogRepository;
import ar.edu.unicen.ticketya.orders.web.RequestIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bitácora de operaciones críticas (tarea 9.3, usada por 6.4). Cada escritura
 * se ejecuta en una transacción propia: si la auditoría falla, se captura fuera
 * de esa transacción para que el fallo no marque como rollback-only ni revierta
 * la operación de negocio que la originó.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String ENTIDAD_ENTRADA = "ENTRADA";

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void ingresoValidado(UUID entradaId, RequestIdentity identity) {
        registrar("INGRESO_VALIDADO", ENTIDAD_ENTRADA, entradaId, identity,
                ResultadoAuditoria.EXITOSO, Map.of("resultado", "ok"));
    }

    public void ingresoRechazado(UUID entradaId, RequestIdentity identity, String motivo) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("resultado", "rechazado");
        detalle.put("motivo", motivo == null ? "" : motivo);
        registrar("INGRESO_RECHAZADO", ENTIDAD_ENTRADA, entradaId, identity,
                ResultadoAuditoria.RECHAZADO, detalle);
    }

    /** Registra otras operaciones de negocio (compra, devolución, cancelación). */
    public void registrar(String operacion, String entidad, UUID entidadId, RequestIdentity identity,
                          ResultadoAuditoria resultado, Map<String, Object> detalle) {
        String detalleJson = json(detalle);
        UUID usuarioId = identity == null ? null : identity.userId();
        String rol = identity == null ? null : identity.role();
        try {
            transactionTemplate.executeWithoutResult(status -> repository.save(new AuditLog(
                    operacion, entidad, entidadId, usuarioId, rol, resultado,
                    detalleJson, CorrelationContext.id())));
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar la auditoría {}: {}", operacion, ex.getMessage());
        }
    }

    private String json(Map<String, Object> detalle) {
        try {
            return objectMapper.writeValueAsString(detalle == null ? Map.of() : detalle);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
