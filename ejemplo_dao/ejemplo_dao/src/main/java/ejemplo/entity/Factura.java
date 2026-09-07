package ejemplo.entity;

import lombok.*;

import java.time.LocalDateTime;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Factura {
    private int idFactura;
    private int idCliente;
}

