package ar.edu.unicen.ticketya.recommendation.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de una compra pagada, usado para recomendaciones y co-compra.
 * Es idempotente por (eventId, clienteId, eventoId): el mismo evento de
 * dominio no se inserta dos veces.
 */
@Document(collection = "historial_compras")
public class HistorialCompra {

    @Id
    private String id;
    private String eventId;
    private String eventoId;
    private String clienteId;
    private String tipo;
    private List<String> artistas = new ArrayList<>();
    private String ciudad;
    private Instant fechaEvento;
    private String ordenId;
    private Instant registradoEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventoId() {
        return eventoId;
    }

    public void setEventoId(String eventoId) {
        this.eventoId = eventoId;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public List<String> getArtistas() {
        return artistas;
    }

    public void setArtistas(List<String> artistas) {
        this.artistas = artistas == null ? new ArrayList<>() : new ArrayList<>(artistas);
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public Instant getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(Instant fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public String getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(String ordenId) {
        this.ordenId = ordenId;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public void setRegistradoEn(Instant registradoEn) {
        this.registradoEn = registradoEn;
    }
}
