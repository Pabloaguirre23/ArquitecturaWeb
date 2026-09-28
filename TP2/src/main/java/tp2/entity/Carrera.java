package tp2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carrera")
@Getter @Setter @NoArgsConstructor
public class Carrera {

    @Id
    @Column(name = "id_carrera")
    private int id;

    @Column(nullable = false)
    private String nombre;

    private int duracion;

    @OneToMany(mappedBy = "carrera", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstudianteCarrera> inscriptos = new ArrayList<>();

    public Carrera(int id, String nombre, int duracion) {
        this.id = id;
        this.nombre = nombre;
        this.duracion = duracion;
    }

    @Override
    public String toString() {
        return "Carrera{id=" + id + ", nombre='" + nombre + "'}";
    }
}
