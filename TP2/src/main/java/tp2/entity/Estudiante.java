package modelo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import modelo.Inscripcion;

@Entity
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "DNI", nullable = false, unique = true)
    private int numeroDocumento;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    private int edad;

    private String genero;

    @Column(name = "ciudad")
    private String ciudadResidencia;

    @Column(name = "LU")
    private int numeroLibreta;

    @OneToMany(mappedBy = "estudiante", fetch = FetchType.LAZY)
    private List<Inscripcion> inscripciones;

    public Estudiante() {
        this.inscripciones = new ArrayList<>();
    }

    public Estudiante(int id, int numeroDocumento, String nombre, String apellido, int edad, String genero,
                      String ciudadResidencia, int numeroLibreta) {
        this();
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.edad = edad;
        this.genero = genero;
        this.numeroDocumento = numeroDocumento;
        this.ciudadResidencia = ciudadResidencia;
        this.numeroLibreta = numeroLibreta;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public int getEdad() { return edad; }
    public void setEdad(int edad) { this.edad = edad; }
    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }
    public int getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(int numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public String getCiudadResidencia() { return ciudadResidencia; }
    public void setCiudadResidencia(String ciudadResidencia) { this.ciudadResidencia = ciudadResidencia; }
    public int getNumeroLibreta() { return numeroLibreta; }
    public void setNumeroLibreta(int numeroLibreta) { this.numeroLibreta = numeroLibreta; }
    public List<Inscripcion> getInscripciones() { return inscripciones; }

    @Override
    public String toString() {
        return "Estudiante [id=" + id + ", nombres=" + nombre + ", apellido=" + apellido + "]";
    }
}
