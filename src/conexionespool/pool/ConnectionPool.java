package conexionespool.pool;

import java.sql.Connection;

public interface ConnectionPool {
    // Solicita una conexión del pool (posiblemente esperando hasta un timeout).
    Connection getConnection() throws InterruptedException;

    // Devuelve una conexión al pool para que pueda ser reciclada.
    void releaseConnection(Connection connection);
}