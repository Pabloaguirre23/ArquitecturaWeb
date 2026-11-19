package ar.edu.unicen.ticketya.orders.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orden")
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "cliente_email", length = 320)
    private String clienteEmail;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "evento_organizador_id")
    private UUID eventoOrganizadorId;

    @Column(name = "evento_nombre", nullable = false, length = 200)
    private String eventoNombre;

    @Column(name = "evento_fecha", nullable = false)
    private OffsetDateTime eventoFecha;

    @Column(name = "sector_nombre", length = 120)
    private String sectorNombre;

    @Column(name = "evento_tipo", length = 40)
    private String eventoTipo;

    /** Snapshot de artistas como arreglo JSON (por ejemplo {@code ["Los Piojos"]}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evento_artistas", nullable = false, columnDefinition = "jsonb")
    private String eventoArtistas = "[]";

    @Column(name = "evento_ciudad", length = 120)
    private String eventoCiudad;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoOrden estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", length = 40)
    private MedioPago medioPago;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @jakarta.persistence.PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public void setClienteId(UUID clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public void setClienteEmail(String clienteEmail) {
        this.clienteEmail = clienteEmail;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    public UUID getEventoOrganizadorId() {
        return eventoOrganizadorId;
    }

    public void setEventoOrganizadorId(UUID eventoOrganizadorId) {
        this.eventoOrganizadorId = eventoOrganizadorId;
    }

    public String getEventoNombre() {
        return eventoNombre;
    }

    public void setEventoNombre(String eventoNombre) {
        this.eventoNombre = eventoNombre;
    }

    public OffsetDateTime getEventoFecha() {
        return eventoFecha;
    }

    public void setEventoFecha(OffsetDateTime eventoFecha) {
        this.eventoFecha = eventoFecha;
    }

    public String getSectorNombre() {
        return sectorNombre;
    }

    public void setSectorNombre(String sectorNombre) {
        this.sectorNombre = sectorNombre;
    }

    public String getEventoTipo() {
        return eventoTipo;
    }

    public void setEventoTipo(String eventoTipo) {
        this.eventoTipo = eventoTipo;
    }

    public String getEventoArtistas() {
        return eventoArtistas;
    }

    public void setEventoArtistas(String eventoArtistas) {
        this.eventoArtistas = eventoArtistas;
    }

    public String getEventoCiudad() {
        return eventoCiudad;
    }

    public void setEventoCiudad(String eventoCiudad) {
        this.eventoCiudad = eventoCiudad;
    }

    public UUID getReservaId() {
        return reservaId;
    }

    public void setReservaId(UUID reservaId) {
        this.reservaId = reservaId;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrden estado) {
        this.estado = estado;
    }

    public MedioPago getMedioPago() {
        return medioPago;
    }

    public void setMedioPago(MedioPago medioPago) {
        this.medioPago = medioPago;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
