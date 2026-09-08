package ejemplo.entity;

public class Cliente {
    private int idCliente;
    private String nombreC;
    private String email;

    public Cliente(int idCliente, String nombreC, String email) {
        this.idCliente = idCliente;
        this.nombreC = nombreC;
        this.email = email;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public String getNombreC() {
        return nombreC;
    }

    public String getEmail() {
        return email;
    }
}
