package ar.edu.unicen.ticketya.recommendation.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Preferencias declaradas por un cliente. Hay como máximo un documento por
 * cliente; el índice único se crea al arrancar el servicio.
 */
@Document(collection = "preferencias")
public class Preferencia {

    @Id
    private String id;
    private String clienteId;
    private List<String> tipos = new ArrayList<>();
    private List<String> artistas = new ArrayList<>();
    private String ciudad;
    private Instant actualizadoEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public List<String> getTipos() {
        return tipos;
    }

    public void setTipos(List<String> tipos) {
        this.tipos = tipos == null ? new ArrayList<>() : new ArrayList<>(tipos);
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

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
