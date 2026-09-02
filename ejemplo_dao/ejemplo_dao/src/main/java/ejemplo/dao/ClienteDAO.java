package ejemplo.dao;

import ejemplo.entity.DetallePedido;

import java.util.List;

public interface ClienteDao {
    public int insertCliente(int idCliente, String nombre, String email) throws SQLException;
    public void updateCliente(Cliente cliente) throws SQLException;
    public void deleteCliente(int idCliente) throws SQLException;
    public Cliente getCliente(int idCliente) throws SQLException;
    public List<Cliente> getClientes() throws SQLException;
    public List<Cliente> getMasFacturadosOrdenados() throws SQLException;
}

