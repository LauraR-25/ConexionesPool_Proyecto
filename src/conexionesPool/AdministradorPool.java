package conexionesPool;

import java.sql.Connection;
import java.sql.SQLException;

public class AdministradorPool {
    private final IPoolConexiones pool;

    public AdministradorPool(IPoolConexiones pool) {
        this.pool = pool;
    }

    public Connection tomarConexion() throws SQLException, InterruptedException {
        return pool.obtener();
    }

    public void devolverConexion(Connection conexion) {
        pool.liberar(conexion);
    }

    public void cerrarPool() throws SQLException {
        pool.cerrarTodo();
    }

    public void mostrarEstadisticas() {
        System.out.println("[Pool] Activas: " + pool.getConexionesActivas() +
                ", Disponibles: " + pool.getConexionesDisponibles() +
                ", Creadas total: " + pool.getTotalCreadas());
    }
}