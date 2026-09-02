package main.java.ejemplo.entity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class DetallePedido {
    private int idCliente;
    private String nombreC;
    private String email;
}
