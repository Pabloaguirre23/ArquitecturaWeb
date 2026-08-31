package ejemplo.repository.mysql;

import ejemplo.dao.ProductoDAO;

import java.sql.*;

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

}

