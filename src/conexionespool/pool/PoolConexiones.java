package conexionespool.pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PoolConexiones implements IPoolConexiones {
    private final List<Connection> disponibles;
    private final List<Connection> enUso;
    private final int maxConexiones;
    private final String url;
    private final String usuario;
    private final String password;
    private int totalCreadas = 0;

    public PoolConexiones(String url, String usuario, String password, int maxConexiones) {
        this.url = url;
        this.usuario = usuario;
        this.password = password;
        this.maxConexiones = maxConexiones;
        this.disponibles = new ArrayList<>();
        this.enUso = new ArrayList<>();
    }

    private Connection crearNuevaConexion() throws SQLException {
        Connection conn = DriverManager.getConnection(url, usuario, password);
        totalCreadas++;
        return conn;
    }

    @Override
    public synchronized Connection obtener() throws SQLException, InterruptedException {
        while (disponibles.isEmpty() && enUso.size() >= maxConexiones) {
            // Esperar hasta que haya una conexión disponible
            wait();
        }
        Connection conn;
        if (!disponibles.isEmpty()) {
            conn = disponibles.remove(disponibles.size() - 1);
        } else {
            conn = crearNuevaConexion();
        }
        enUso.add(conn);
        return conn;
    }

    @Override
    public synchronized void liberar(Connection conexion) {
        if (conexion != null && enUso.remove(conexion)) {
            disponibles.add(conexion);
            notifyAll(); // Notificar a los hilos que esperan
        }
    }

    @Override
    public synchronized void cerrarTodo() throws SQLException {
        for (Connection conn : disponibles) {
            conn.close();
        }
        for (Connection conn : enUso) {
            conn.close();
        }
        disponibles.clear();
        enUso.clear();
    }

    @Override
    public synchronized int getConexionesActivas() {
        return enUso.size();
    }

    @Override
    public synchronized int getConexionesDisponibles() {
        return disponibles.size();
    }

    @Override
    public synchronized int getTotalCreadas() {
        return totalCreadas;
    }
}