package ejemplo.entity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Producto {
    private int idProducto;
    private String nombreP;
    private Float valor;
}
