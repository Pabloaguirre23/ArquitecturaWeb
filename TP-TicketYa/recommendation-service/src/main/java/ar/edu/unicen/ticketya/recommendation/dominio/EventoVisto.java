package ar.edu.unicen.ticketya.recommendation.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Snapshot mínimo de un evento para calcular fallbacks y motivos sin depender
 * de catalog en cada consulta. Se actualiza a partir de los eventos de dominio.
 */
@Document(collection = "eventos_vistos")
public class EventoVisto {

    @Id
    private String eventoId;
    private String nombre;
    private String tipo;
    private List<String> artistas = new ArrayList<>();
    private String ciudad;
    private Instant fechaEvento;
    private String estado;
    private Instant actualizadoEn;

    public String getEventoId() {
        return eventoId;
    }

    public void setEventoId(String eventoId) {
        this.eventoId = eventoId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
