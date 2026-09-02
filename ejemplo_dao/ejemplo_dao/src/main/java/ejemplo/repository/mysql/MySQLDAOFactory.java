package main.java.ejemplo.repository.mysql;

import java.sql.Connection;
import java.sql.Statement;

import ejemplo.dao.*;
import ejemplo.factory.DAOFactory;

public class MySQLDAOFactory extends DAOFactory {

    @Override
    protected Connection getConnection() {
        return MySQLConnectionManager.getInstance().getConnection();
    }

    @Override
    protected void doShutdown() {
        MySQLConnectionManager.getInstance().shutdown();
    }

    @Override
    public ProductoDAO createUsuarioDAO() {
        // Devuelve la implementación concreta MySQL de UsuarioDAO
        return new MySQLProductoDAO(getConnection());
    }

    @Override
    public FacturaProductoDAO createFacturaProductoDAO() {
        // Devuelve la implementación concreta MySQL de ProductoDAO
        return new MySQLFacturaProductoDAO(getConnection());
    }

    @Override
    public FacturaDAO createFacturaDAO() {
        return new MySQLFacturaDAO(getConnection());
    }

    @Override
    public ClienteDAO createClienteDAO() {
        return new MySQLClienteDAO(getConnection());
    }
}
