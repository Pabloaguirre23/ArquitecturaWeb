package ejemplo.entity;

import lombok.*;

import java.time.LocalDateTime;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Pedido {
    private Long id;
    private Long usuarioId;           // FK -> usuarios.id
    private PedidoEstado estado;      // BORRADOR / PAGADO / ENVIADO / CANCELADO
    private Float total;              // total del pedido


}

