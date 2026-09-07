package ejemplo.repository.mysql;

import ejemplo.dao.ProductoDAO;
import ejemplo.entity.Producto;

import java.sql.*;
import java.util.List;

public class MySQLProductoDAO implements ProductoDAO {

    private final Connection cn;

    public MySQLProductoDAO(Connection cn) {
        this.cn = cn;
        crearTablasSiNoExisten();
    }

    private void crearTablasSiNoExisten() {
        final String sqlProducto = "CREATE TABLE IF NOT EXISTS producto (" +
                "idProducto INT PRIMARY KEY AUTO_INCREMENT," +
                "nombre VARCHAR(45) NOT NULL," +
                "valor FLOAT" +
                ")";
        try (Statement st = cn.createStatement()) {
            st.execute(sqlProducto);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando el esquema", e);
        }
    }

    @Override
    public int insertProducto(int idProducto, String nombre, int valor) throws SQLException {
        return 0;
    }

    @Override
    public void updateProducto(Producto idProducto) throws SQLException {

    }

    @Override
    public void deleteProducto(int idProducto) throws SQLException {

    }

    @Override
    public Producto getProducto(int idProducto) throws SQLException {
        return null;
    }

    @Override
    public List<Producto> getProductos() throws SQLException {
        return List.of();
    }

    @Override
    public Producto getProductoMasVendido() throws SQLException {
        return null;
    }
}

