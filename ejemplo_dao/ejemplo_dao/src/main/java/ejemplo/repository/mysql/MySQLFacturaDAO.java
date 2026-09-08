package ejemplo.repository.mysql;

import ejemplo.dao.FacturaDAO;
import ejemplo.dto.TopProducto;
import ejemplo.entity.Factura;


import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;



public class MySQLFacturaDAO implements FacturaDAO {

    private final Connection cn;

    public MySQLFacturaDAO(Connection cn) {
        this.cn = cn;
        crearTablasSiNoExisten();
    }

    private void crearTablasSiNoExisten() {
        final String sqlCliente = "CREATE TABLE IF NOT EXISTS cliente (" +
                "idCliente INT PRIMARY KEY AUTO_INCREMENT," +
                "nombre VARCHAR(500) NOT NULL," +
                "email VARCHAR(150)" +
                ")";
        final String sqlFactura = "CREATE TABLE IF NOT EXISTS factura (" +
                "idFactura INT PRIMARY KEY AUTO_INCREMENT," +
                "idCliente INT NOT NULL," +
                "INDEX idx_factura_cliente (idCliente)," +
                "CONSTRAINT fk_factura_cliente FOREIGN KEY (idCliente) REFERENCES cliente(idCliente)" +
                ")";
        try (Statement st = cn.createStatement()) {
            st.execute(sqlCliente);
            st.execute(sqlFactura);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando el esquema", e);
        }
    }

    @Override
    public int insertFactura(int idFactura, int idCliente) throws SQLException {
        String sql = "INSERT INTO factura (idFactura, idCliente) VALUES (?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idFactura);
            ps.setInt(2, idCliente);
            return ps.executeUpdate();
        }
    }

    @Override
    public void updateFactura(Factura idFactura) throws SQLException {

    }

    @Override
    public void deleteFactura(int idFactura) throws SQLException {

    }

    @Override
    public Factura getFactura(int idFactura) throws SQLException {
        return null;
    }

    @Override
    public List<Factura> getPFacturas() throws SQLException {
        return List.of();
    }
}

