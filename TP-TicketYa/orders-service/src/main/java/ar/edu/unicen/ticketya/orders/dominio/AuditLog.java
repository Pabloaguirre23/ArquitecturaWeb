package ar.edu.unicen.ticketya.orders.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bitácora persistente de operaciones críticas (compra, devolución, cancelación e ingreso).
 * El detalle se guarda como JSON libre para no acoplar la tabla a cada caso de uso.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String operacion;

    @Column(nullable = false, length = 40)
    private String entidad;

    @Column(name = "entidad_id")
    private UUID entidadId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(length = 20)
    private String rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoAuditoria resultado;

    @Column(columnDefinition = "jsonb")
    private String detalle;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AuditLog() {
    }

    public AuditLog(String operacion, String entidad, UUID entidadId, UUID usuarioId, String rol,
                    ResultadoAuditoria resultado, String detalle, String correlationId) {
        this.operacion = operacion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.usuarioId = usuarioId;
        this.rol = rol;
        this.resultado = resultado;
        this.detalle = detalle;
        this.correlationId = correlationId;
    }

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOperacion() {
        return operacion;
    }

    public void setOperacion(String operacion) {
        this.operacion = operacion;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public UUID getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(UUID entidadId) {
        this.entidadId = entidadId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public ResultadoAuditoria getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoAuditoria resultado) {
        this.resultado = resultado;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
