package tp3.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "inscripcion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"estudiante_id", "carrera_id"}))
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrera_id", nullable = false)
    private Carrera carrera;

    @Column(name = "fecha_inscripcion", nullable = false)
    private LocalDate fechaInscripcion;

    @Column(name = "fecha_egreso")
    private LocalDate fechaEgreso;

    @Column(name = "egresado", nullable = false)
    private Boolean egresado = Boolean.FALSE;

    public Inscripcion() {
    }

    public Inscripcion(Estudiante estudiante, Carrera carrera, LocalDate fechaInscripcion) {
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.fechaInscripcion = fechaInscripcion;
    }

    public void marcarEgresado(LocalDate fechaEgreso) {
        this.egresado = Boolean.TRUE;
        this.fechaEgreso = fechaEgreso;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDate getFechaInscripcion() {
        return fechaInscripcion;
    }

    public void setFechaInscripcion(LocalDate fechaInscripcion) {
        this.fechaInscripcion = fechaInscripcion;
    }

    public LocalDate getFechaEgreso() {
        return fechaEgreso;
    }

    public void setFechaEgreso(LocalDate fechaEgreso) {
        this.fechaEgreso = fechaEgreso;
    }

    public Boolean getEgresado() {
        return egresado;
    }

    public void setEgresado(Boolean egresado) {
        this.egresado = egresado;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Inscripcion)) return false;
        Inscripcion that = (Inscripcion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}