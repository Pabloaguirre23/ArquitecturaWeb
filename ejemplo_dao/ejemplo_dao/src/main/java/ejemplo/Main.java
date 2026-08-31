package ejemplo;

import ejemplo.factory.DAOFactory;
import ejemplo.factory.DBType;

public class Main {

    public static void main(String[] args) {
        DAOFactory f = DAOFactory.getInstance();
        f.createClienteDAO();
        f.createFacturaDAO();
        f.createFacturaProductoDAO();
        f.createUsuarioDAO();
    }

}
