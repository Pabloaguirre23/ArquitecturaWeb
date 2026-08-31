package ejemplo.entity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class DetallePedido {
    private Long id;
    private Long pedidoId;        // FK -> pedidos.id
    private Long productoId;      // FK -> productos.id
    private Integer cantidad;
    private Float precioUnitario; // precio capturado en el momento
    private Float subtotal;       // cantidad * precioUnitario


}
