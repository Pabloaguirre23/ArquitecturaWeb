package ejemplo.entity;

public class Producto {
    private int idProducto;
    private String nombreP;
    private Float valor;

    public Producto (int idProducto, String nombreP, Float valor) {
        this.idProducto = idProducto;
        this.nombreP = nombreP;
        this.valor = valor;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public String getNombreP() {
        return nombreP;
    }

    public Float getValor() {
        return valor;
    }
}
