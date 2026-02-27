package conexionespool.pool;

import java.sql.Connection;
import java.sql.SQLException;

public interface IPoolConexiones {
    Connection obtener() throws SQLException, InterruptedException;
    void liberar(Connection conexion);
    void cerrarTodo() throws SQLException;
    int getConexionesActivas();
    int getConexionesDisponibles();
    int getTotalCreadas();
}