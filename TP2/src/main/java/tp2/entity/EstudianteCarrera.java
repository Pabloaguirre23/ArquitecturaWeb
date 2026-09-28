package tp2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "estudiante_carrera")
@Getter @Setter @NoArgsConstructor
public class EstudianteCarrera {

    @Id
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dni_estudiante")
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_carrera")
    private Carrera carrera;

    private int inscripcion;

    /** Año de graduación. 0 o null = aún no graduado (el CSV usa 0). */
    private Integer graduacion;

    private int antiguedad;

    public EstudianteCarrera(int id, Estudiante estudiante, Carrera carrera, int inscripcion, Integer graduacion, int antiguedad) {
        this.id = id;
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.graduacion = (graduacion != null && graduacion == 0) ? null : graduacion;
        this.antiguedad = antiguedad;
    }

    public boolean isGraduado() {
        return graduacion != null && graduacion != 0;
    }
}
