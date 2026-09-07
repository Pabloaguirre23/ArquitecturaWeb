package ejemplo.repository.mysql;

import ejemplo.dao.ClienteDAO;
import ejemplo.entity.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLClienteDAO implements ClienteDAO {

    private final Connection cn;

    public MySQLClienteDAO(Connection cn) {
        this.cn = cn;
        crearTablasSiNoExisten();
    }

    private void crearTablasSiNoExisten() {
        // Asegura tablas referenciadas y crea factura_producto al final
        final String sqlCliente = "CREATE TABLE IF NOT EXISTS cliente (" +
                "idCliente INT PRIMARY KEY AUTO_INCREMENT," +
                "nombre VARCHAR(500) NOT NULL," +
                "email VARCHAR(150)" +
                ")";
        try (Statement st = cn.createStatement()) {
            st.execute(sqlCliente);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando el esquema", e);
        }
    }

    @Override
    public int insertCliente(int idCliente, String nombre, String email) throws SQLException {
        return 0;
    }

    @Override
    public void updateCliente(Cliente cliente) throws SQLException {

    }

    @Override
    public void deleteCliente(int idCliente) throws SQLException {

    }

    @Override
    public Cliente getCliente(int idCliente) throws SQLException {
        return null;
    }

    @Override
    public List<Cliente> getClientes() throws SQLException {
        return List.of();
    }

    @Override
    public List<Cliente> getMasFacturadosOrdenados() throws SQLException {
        return List.of();
    }
}

