package ar.edu.unicen.ticketya.orders.repositorio;

import ar.edu.unicen.ticketya.orders.dominio.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);

    List<AuditLog> findByEntidadAndEntidadIdOrderByCreatedAtDesc(String entidad, UUID entidadId);
}
