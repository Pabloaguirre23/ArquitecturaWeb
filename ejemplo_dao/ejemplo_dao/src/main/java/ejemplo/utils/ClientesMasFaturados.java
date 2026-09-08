package ejemplo.utils;

import java.sql.SQLException;
import java.util.List;

import ejemplo.dao.ClienteDAO;
import ejemplo.entity.Cliente;
import ejemplo.factory.DAOFactory;

public class ClientesMasFacturados {

    private final ClienteDAO clienteDAO;

    public ClientesMasFacturados() {
        DAOFactory f = DAOFactory.getInstance();
        this.clienteDAO = f.createClienteDAO();
    }

    public void run() {
        try {
            List<Cliente> lista = clienteDAO.getMasFacturadosOrdenados();
            System.out.println("Clientes ordenados por monto facturado (mayor a menor):");
            for (Cliente cliente : lista) {
                System.out.println(cliente.getIdCliente() + " - " + cliente.getNombreC());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error obteniendo el ranking de clientes facturados", e);
        }
    }
}