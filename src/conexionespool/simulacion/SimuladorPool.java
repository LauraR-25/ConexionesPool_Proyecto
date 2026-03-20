package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.pool.AdministradorPool;
import conexionespool.util.Freno;

import java.sql.*;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimuladorPool {
    private final int totalMuestras;
    private final int reintentosMaximos;
    private final Supplier<String> proveedorQuery;
    private final Freno freno;
    private final Random random = new Random();
    private final AdministradorPool admin;

    public SimuladorPool(int totalMuestras, int reintentosMaximos, Supplier<String> proveedorQuery,
                         Freno freno, AdministradorPool admin) {
        this.totalMuestras = totalMuestras;
        this.reintentosMaximos = reintentosMaximos;
        this.proveedorQuery = proveedorQuery;
        this.freno = freno;
        this.admin = admin;
    }

    public void ejecutar(ContadorEstadisticas contador, Consumer<Double> actualizadorProgreso) {
        Thread[] hilos = new Thread[totalMuestras];
        for (int i = 0; i < totalMuestras; i++) {
            final int id = i + 1;
            final String query = proveedorQuery.get();
            hilos[i] = new Thread(() -> {
                if (freno.estaActivado()) return;
                ejecutarMuestra(id, query, contador, actualizadorProgreso);
            });
            hilos[i].start();
        }

        for (Thread h : hilos) {
            try {
                h.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void ejecutarMuestra(int id, String query, ContadorEstadisticas contador, Consumer<Double> actualizador) {
        boolean exito = false;
        String mensajeError = "";
        int reintentos = 0;
        Connection conn = null;

        while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
            try {
                conn = admin.tomarConexion();
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery(query);
                    if (rs.next()) exito = true;
                    rs.close();
                } finally {
                    admin.devolverConexion(conn);
                }
                break;
            } catch (SQLException e) {
                mensajeError = e.getMessage();
                reintentos++;
                // Sin logs
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Resultado resultado = new Resultado(id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis());
        contador.registrar(resultado);
        actualizador.accept((double) id / totalMuestras);
    }
}