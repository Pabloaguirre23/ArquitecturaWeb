package ar.edu.unicen.ticketya.orders.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Estado persistido de la saga de compra. Una fila por orden; la clave es la orden para
 * poder reanudar o compensar el flujo después de un reinicio.
 */
@Entity
@Table(name = "saga_compra")
public class SagaCompra {

    @Id
    @Column(name = "orden_id", nullable = false)
    private UUID ordenId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PasoSaga paso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSaga estado = EstadoSaga.EN_PROCESO;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error")
    private String ultimoError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public SagaCompra() {
    }

    public SagaCompra(UUID ordenId, PasoSaga paso) {
        this.ordenId = ordenId;
        this.paso = paso;
        this.estado = EstadoSaga.EN_PROCESO;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Avanza el último paso completado; un reintento idempotente puede repetir el mismo paso. */
    public void avanzar(PasoSaga siguiente) {
        if (siguiente.ordinal() > paso.ordinal()) {
            paso = siguiente;
        }
        intentos = 0;
        ultimoError = null;
    }

    public void registrarError(String error) {
        intentos++;
        ultimoError = error;
    }

    public UUID getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(UUID ordenId) {
        this.ordenId = ordenId;
    }

    public PasoSaga getPaso() {
        return paso;
    }

    public void setPaso(PasoSaga paso) {
        this.paso = paso;
    }

    public EstadoSaga getEstado() {
        return estado;
    }

    public void setEstado(EstadoSaga estado) {
        this.estado = estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
