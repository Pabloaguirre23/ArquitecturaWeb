package ejemplo.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import ejemplo.dao.ClienteDAO;
import ejemplo.dao.FacturaDAO;
import ejemplo.dao.FacturaProductoDAO;
import ejemplo.dao.ProductoDAO;
import ejemplo.factory.DAOFactory;

public class CargarDatosCSV {

    private final ClienteDAO clienteDAO;
    private final ProductoDAO productoDAO;
    private final FacturaDAO facturaDAO;
    private final FacturaProductoDAO facturaProductoDAO;

    public CargarDatosCSV() {
        DAOFactory f = DAOFactory.getInstance();
        this.clienteDAO = f.createClienteDAO();
        this.productoDAO = f.createProductoDAO();
        this.facturaDAO = f.createFacturaDAO();
        this.facturaProductoDAO = f.createFacturaProductoDAO();
    }

    public void run() {
        cargarClientes("clientes.csv");
        cargarProductos("productos.csv");
        cargarFacturas("facturas.csv");
        cargarFacturaProducto("facturas-productos.csv");
        System.out.println("Carga completa de datos desde CSV.");
    }

    /** Abre un archivo ubicado en src/main/resources como recurso del classpath. */
    private InputStream abrirRecurso(String nombreArchivo) {
        InputStream is = getClass().getResourceAsStream("/" + nombreArchivo);
        if (is == null) {
            throw new RuntimeException("No se encontró el recurso: " + nombreArchivo
                    + " (¿está en src/main/resources?)");
        }
        return is;
    }

    private void cargarClientes(String ruta) {
        try (CSVParser parser = CSVFormat.DEFAULT.withHeader()
                .parse(new InputStreamReader(abrirRecurso(ruta)))) {
            for (CSVRecord row : parser) {
                int idCliente = Integer.parseInt(row.get("idCliente"));
                String nombre = row.get("nombre");
                String email = row.get("email");
                clienteDAO.insertCliente(idCliente, nombre, email);
            }
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Error cargando clientes desde " + ruta, e);
        }
    }

    private void cargarProductos(String ruta) {
        try (CSVParser parser = CSVFormat.DEFAULT.withHeader()
                .parse(new InputStreamReader(abrirRecurso(ruta)))) {
            for (CSVRecord row : parser) {
                int idProducto = Integer.parseInt(row.get("idProducto"));
                String nombre = row.get("nombre");
                int valor = Integer.parseInt(row.get("valor"));
                productoDAO.insertProducto(idProducto, nombre, valor);
            }
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Error cargando productos desde " + ruta, e);
        }
    }

    private void cargarFacturas(String ruta) {
        try (CSVParser parser = CSVFormat.DEFAULT.withHeader()
                .parse(new InputStreamReader(abrirRecurso(ruta)))) {
            for (CSVRecord row : parser) {
                int idFactura = Integer.parseInt(row.get("idFactura"));
                int idCliente = Integer.parseInt(row.get("idCliente"));
                facturaDAO.insertFactura(idFactura, idCliente);
            }
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Error cargando facturas desde " + ruta, e);
        }
    }

    private void cargarFacturaProducto(String ruta) {
        try (CSVParser parser = CSVFormat.DEFAULT.withHeader()
                .parse(new InputStreamReader(abrirRecurso(ruta)))) {
            for (CSVRecord row : parser) {
                int idFactura = Integer.parseInt(row.get("idFactura"));
                int idProducto = Integer.parseInt(row.get("idProducto"));
                int cantidad = Integer.parseInt(row.get("cantidad"));
                facturaProductoDAO.insertFacturaProducto(idFactura, idProducto, cantidad);
            }
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Error cargando factura_producto desde " + ruta, e);
        }
    }
}