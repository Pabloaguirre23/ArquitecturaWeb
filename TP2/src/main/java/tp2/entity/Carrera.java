package modelo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Carrera {

    @Id
    private int id;

    @Column(name = "carrera", nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int duracion;

    @OneToMany(mappedBy = "carrera", fetch = FetchType.LAZY)
    private List<Inscripcion> inscripciones;

    public Carrera() {
        this.inscripciones = new ArrayList<>();
    }

    public Carrera(int id, String nombre) {
        this();
        this.id = id;
        this.nombre = nombre;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getDuracion() { return duracion; }
    public void setDuracion(int duracion) { this.duracion = duracion; }
    public List<Inscripcion> getInscripciones() { return inscripciones; }

    @Override
    public String toString() {
        return "Carrera [id=" + id + ", nombre=" + nombre + "]";
    }
}
