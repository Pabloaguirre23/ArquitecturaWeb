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
        String sql = "INSERT INTO cliente (idCliente, nombre, email) VALUES (?, ?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.setString(2, nombre);
            if (email != null && !email.isEmpty()) {
                ps.setString(3, email);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            return ps.executeUpdate();
        }
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
        String sql = "SELECT c.idCliente, c.nombre, c.email, COALESCE(SUM(fp.cantidad * p.valor), 0) AS total " +
                "FROM cliente c LEFT JOIN factura f ON c.idCliente = f.idCliente " +
                "LEFT JOIN factura_producto fp ON f.idFactura = fp.idFactura " +
                "LEFT JOIN producto p ON fp.idProducto = p.idProducto " +
                "GROUP BY c.idCliente, c.nombre, c.email " +
                "ORDER BY total DESC, c.idCliente ASC";
        List<Cliente> lista = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Cliente(rs.getInt("idCliente"), rs.getString("nombreC"), rs.getString("email")));
            }
        }
        return lista;
    }
}

