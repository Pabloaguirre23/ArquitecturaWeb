package ejemplo.utils;

import java.sql.SQLException;

import ejemplo.dao.ProductoDAO;
import ejemplo.entity.Producto;
import ejemplo.factory.DAOFactory;

public class ProductoMasRecaudado {

    private final ProductoDAO productoDAO;

    public ProductoMasRecaudado() {
        DAOFactory f = DAOFactory.getInstance();
        this.productoDAO = f.createProductoDAO();
    }

    public void run() {
        try {
            Producto producto = productoDAO.getProductoMasRecaudado();
            if (producto != null) {
                System.out.println("Producto que más recaudó: " + producto.getNombreP()
                        + " (idProducto=" + producto.getIdProducto()
                        + ", valor unitario=" + producto.getValor() + ")");
            } else {
                System.out.println("No hay ventas registradas todavía.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error obteniendo el producto más recaudado", e);
        }
    }
}
