package ejemplo.dto;

import java.math.BigDecimal;

/**
 * record de Java, es una característica desde Java 16, que sirve para declarar una clase inmutable y compacta para agrupar datos.
 * Equivale a una clase con atributos, constructor, getters, toString(), equals() y hashCode() generados automáticamente.
 * En este caso, TopProductStats guarda tres cosas:
 * producto → nombre del producto (String)
 * unidades → cantidad total pedida (long)
 * ingreso → monto total (BigDecimal)
 * @param producto
 * @param unidades
 * @param ingreso
 */
public record TopProducto(String producto, long unidades, BigDecimal ingreso) {}

