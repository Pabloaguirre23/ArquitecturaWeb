package main.java.ejemplo.dao;


public interface ProductoDAO {
    public int insertProducto(int idProducto, String nombre, int valor) throws SQLException;
    public void updateProducto(Producto idProducto) throws SQLException;
    public void deleteProducto(int idProducto) throws SQLException;
    public Producto getProducto(int idProducto) throws SQLException;
    public List<Producto> getProductos() throws SQLException;
    public Producto getProductoMasVendido() throws SQLException;
}


