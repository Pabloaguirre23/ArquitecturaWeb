package tp2.modelo;

import jakarta.persistence.*;

@Entity
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "id_carrera", nullable = false)
    private Carrera carrera;

    private int inscripcion; // año de inscripción
    private int graduacion;  // año de graduación, 0 = no graduado
    private int antiguedad;  // años de antigüedad en esa carrera

    public Inscripcion() {
    }

    public Inscripcion(Estudiante estudiante, Carrera carrera, int inscripcion, int graduacion, int antiguedad) {
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
        this.antiguedad = antiguedad;
    }

    public int getId() {
        return id;
    }

    public Estudiante getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public Carrera getCarrera() {
        return carrera;
    }

    public void setCarrera(Carrera carrera) {
        this.carrera = carrera;
    }

    public int getInscripcion() {
        return inscripcion;
    }

    public void setInscripcion(int inscripcion) {
        this.inscripcion = inscripcion;
    }

    public int getGraduacion() {
        return graduacion;
    }

    public void setGraduacion(int graduacion) {
        this.graduacion = graduacion;
    }

    public int getAntiguedad() {
        return antiguedad;
    }

    public void setAntiguedad(int antiguedad) {
        this.antiguedad = antiguedad;
    }

    @Override
    public String toString() {
        return "Inscripcion [id=" + id + ", estudiante=" + (estudiante != null ? estudiante.getNumeroDocumento() : null)
                + ", carrera=" + (carrera != null ? carrera.getId() : null)
                + ", inscripcion=" + inscripcion + ", graduacion=" + graduacion
                + ", antiguedad=" + antiguedad + "]";
    }
}