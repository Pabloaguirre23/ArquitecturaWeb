import ejemplo.utils.ClientesMasFacturados;
import ejemplo.utils.ProductoMasRecaudado;

import java.sql.SQLException;
import java.util.List;

public class Main {

    public static void main(String[] args) throws SQLException {
        CargarTabSiNoExisten CargarTabSiNoExisten = new CargarTabSiNoExisten();
        CargarTabSiNoExisten.run();
        //CargarDatosCSV CargarDatosCSV = new CargarDatosCSV();
        //CargarDatosCSV.run();

        ProductoMasRecaudado ProductoMasRecaudado = new ProductoMasRecaudado();
        ProductoMasRecaudado.run();
        ClientesMasFacturados ClientesMasFacturados = new ClientesMasFacturados();
        ClientesMasFacturados.run();
    }

}