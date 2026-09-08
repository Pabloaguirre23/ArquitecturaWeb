package ejemplo.utils;

import ejemplo.factory.DAOFactory;

public class CargarTabSiNoExisten {

    public void run() {
        DAOFactory f = DAOFactory.getInstance();

        f.createClienteDAO();
        f.createFacturaDAO();
        f.createFacturaProductoDAO();
        f.createProductoDAO();

        System.out.println("Tablas verificadas/creadas correctamente.");
    }

}
