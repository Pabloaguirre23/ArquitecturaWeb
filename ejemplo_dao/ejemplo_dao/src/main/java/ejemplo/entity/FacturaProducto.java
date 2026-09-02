package ejemplo.entity;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class FacturaProducto {
    private int idFactura;
    private int idProducto;
    private int cantidad;
}
