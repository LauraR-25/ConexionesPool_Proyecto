package conexionesPool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolConexiones implements IPoolConexiones {
    private final BlockingQueue<Connection> disponibles;
    private final AtomicInteger creadas = new AtomicInteger(0);
    private final int maxConexiones;
    private final String url;
    private final String usuario;
    private final String password;
    private final long timeoutMs;
    private final AtomicInteger totalCreadas = new AtomicInteger(0);

    public PoolConexiones(String url, String usuario, String password, int maxConexiones, long timeoutMs) {
        this.url = url;
        this.usuario = usuario;
        this.password = password;
        this.maxConexiones = maxConexiones;
        this.timeoutMs = timeoutMs;
        this.disponibles = new LinkedBlockingQueue<>();
        // Conexión inicial opcional
        try {
            disponibles.add(crearNuevaConexion());
        } catch (SQLException e) {
            // No pasa nada, se crearán bajo demanda
        }
    }

    private Connection crearNuevaConexion() throws SQLException {
        Connection conn = DriverManager.getConnection(url, usuario, password);
        creadas.incrementAndGet();
        totalCreadas.incrementAndGet();
        return conn;
    }

    private boolean esValida(Connection conn) {
        try {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private void cerrarConexion(Connection conn) {
        try {
            conn.close();
        } catch (SQLException ignored) {}
        creadas.decrementAndGet();
    }

    @Override
    public Connection obtener() throws SQLException, InterruptedException {
        Connection conn = disponibles.poll();
        if (conn != null) {
            if (esValida(conn)) {
                return conn;
            } else {
                cerrarConexion(conn);
                return obtenerNuevaOEsperar();
            }
        }
        return obtenerNuevaOEsperar();
    }

    private Connection obtenerNuevaOEsperar() throws SQLException, InterruptedException {
        if (creadas.get() < maxConexiones) {
            return crearNuevaConexion();
        } else {
            Connection conn = disponibles.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (conn == null) {
                throw new SQLException("Tiempo de espera agotado (" + timeoutMs + " ms)");
            }
            if (esValida(conn)) {
                return conn;
            } else {
                cerrarConexion(conn);
                return obtenerNuevaOEsperar();
            }
        }
    }

    @Override
    public void liberar(Connection conexion) {
        if (conexion != null) {
            disponibles.offer(conexion);
        }
    }

    @Override
    public void cerrarTodo() throws SQLException {
        for (Connection conn : disponibles) {
            conn.close();
        }
        disponibles.clear();
        creadas.set(0);
    }

    @Override
    public int getConexionesActivas() {
        return creadas.get() - disponibles.size();
    }

    @Override
    public int getConexionesDisponibles() {
        return disponibles.size();
    }

    @Override
    public int getTotalCreadas() {
        return totalCreadas.get();
    }
}