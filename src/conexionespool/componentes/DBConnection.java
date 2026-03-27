package conexionespool.componentes;

// Interfaz que representa una conexión a la base de datos
public interface DBConnection {
    void connect() throws DBException;

    void disconnect() throws DBException;

    boolean isConnected();
}