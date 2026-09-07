package ejemplo.entity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Cliente {
    private int idCliente;
    private String nombreC;
    private String email;
}
