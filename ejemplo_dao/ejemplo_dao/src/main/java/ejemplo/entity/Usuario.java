package ejemplo.entity;

import lombok.*;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Usuario {
    private Long id;
    private String nombre;
    private String email;
    private Integer edad;

}

