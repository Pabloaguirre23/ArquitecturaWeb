package main.java.ejemplo.dao;


import org.ejemplo.entity.Factura;

import java.sql.SQLException;
import java.util.List;

public interface FacturaDao {
    public int insertFactura(int idFactura, int idCliente) throws SQLException;
    public void updateFactura(Factura idFactura) throws SQLException;
    public void deleteFactura(int idFactura) throws SQLException;
    public Factura getFactura(int idFactura) throws SQLException;
    public List<Factura> getPFacturas() throws SQLException;
}
