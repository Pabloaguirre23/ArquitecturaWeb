package tp2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "estudiante")
@Getter @Setter @NoArgsConstructor
public class Estudiante {

    @Id
    @Column(name = "dni")
    private int dni;

    private String nombre;
    private String apellido;
    private int edad;
    private String genero;
    private String ciudad;

    @Column(name = "lu", unique = true, nullable = false)
    private int libretaUniversitaria;

    @OneToMany(mappedBy = "estudiante", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstudianteCarrera> carreras = new ArrayList<>();

    public Estudiante(int dni, String nombre, String apellido, int edad, String genero, String ciudad, int libretaUniversitaria) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.edad = edad;
        this.genero = genero;
        this.ciudad = ciudad;
        this.libretaUniversitaria = libretaUniversitaria;
    }

    @Override
    public String toString() {
        return "Estudiante{dni=" + dni + ", nombre='" + nombre + " " + apellido + "', LU=" + libretaUniversitaria + "}";
    }
}
