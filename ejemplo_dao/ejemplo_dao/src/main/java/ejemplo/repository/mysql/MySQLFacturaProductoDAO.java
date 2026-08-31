package ejemplo.repository.mysql;

import ejemplo.dao.FacturaProductoDAO;
import ejemplo.entity.Producto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLFacturaProductoDAO implements FacturaProductoDAO {

    private final Connection cn;

    public MySQLFacturaProductoDAO(Connection cn) {
        this.cn = cn;
        crearTablasSiNoExisten();
    }

    private void crearTablasSiNoExisten() {
        final String sqlProducto = "CREATE TABLE IF NOT EXISTS producto (" +
                "idProducto INT PRIMARY KEY AUTO_INCREMENT," +
                "nombre VARCHAR(45) NOT NULL," +
                "valor FLOAT" +
                ")";
        final String sqlFactura = "CREATE TABLE IF NOT EXISTS factura (" +
                "idFactura INT PRIMARY KEY AUTO_INCREMENT," +
                "idCliente INT NOT NULL," +
                "INDEX idx_factura_cliente (idCliente)," +
                "CONSTRAINT fk_factura_cliente FOREIGN KEY (idCliente) REFERENCES cliente(idCliente)" +
                ")";
        final String sqlFacturaProducto = "CREATE TABLE IF NOT EXISTS factura_producto (" +
                "idFactura INT NOT NULL," +
                "idProducto INT NOT NULL," +
                "cantidad INT NOT NULL," +
                "PRIMARY KEY (idFactura, idProducto)," +
                "CONSTRAINT fk_fp_factura FOREIGN KEY (idFactura) REFERENCES factura(idFactura) ON DELETE CASCADE," +
                "CONSTRAINT fk_fp_producto FOREIGN KEY (idProducto) REFERENCES producto(idProducto)" +
                ")";
        try (Statement st = cn.createStatement()) {
            st.execute(sqlProducto);
            st.execute(sqlFactura);
            st.execute(sqlFacturaProducto);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando el esquema", e);
        }
    }

}
