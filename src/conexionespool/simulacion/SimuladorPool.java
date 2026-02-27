package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.pool.AdministradorPool;
import conexionespool.util.Freno;
import conexionespool.util.LoggerMuestras;

import java.sql.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimuladorPool {
    private final int totalMuestras;
    private final int reintentosMaximos;
    private final Supplier<String> proveedorQuery;
    private final Freno freno;
    private final LoggerMuestras logger;
    private final AdministradorPool admin;
    private final Random random = new Random();

    public SimuladorPool(int totalMuestras, int reintentosMaximos, Supplier<String> proveedorQuery,
                         Freno freno, LoggerMuestras logger,
                         AdministradorPool admin) {
        this.totalMuestras = totalMuestras;
        this.reintentosMaximos = reintentosMaximos;
        this.proveedorQuery = proveedorQuery;
        this.freno = freno;
        this.logger = logger;
        this.admin = admin;
    }

    public void ejecutar(ContadorEstadisticas contador, Consumer<Double> actualizadorProgreso) {
        int hilosConcurrentes = Math.min(totalMuestras, 100);
        ExecutorService executor = Executors.newFixedThreadPool(hilosConcurrentes);
        final int[] completadas = {0};

        for (int i = 0; i < totalMuestras; i++) {
            final int id = i + 1;
            final String query = proveedorQuery.get();

            executor.submit(() -> {
                if (freno.estaActivado()) return;

                boolean exito = false;
                String mensajeError = "";
                int reintentos = 0;
                Connection conn = null;

                while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
                    if (reintentos > 0) {
                        try {
                            Thread.sleep(random.nextInt(50));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    try {
                        conn = admin.tomarConexion();
                        try (Statement stmt = conn.createStatement()) {
                            ResultSet rs = stmt.executeQuery(query);
                            if (rs.next()) {
                                exito = true;
                            }
                            rs.close();
                        } finally {
                            admin.devolverConexion(conn);
                        }
                        break;
                    } catch (SQLException e) {
                        mensajeError = e.getMessage();
                        reintentos++;
                        logger.log(String.format("Pool %d reintento %d: %s", id, reintentos, mensajeError));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                Resultado resultado = new Resultado(
                        id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis()
                );
                contador.registrar(resultado);
                logger.log(resultado.formatoLog());

                synchronized (completadas) {
                    completadas[0]++;
                    double progreso = (double) completadas[0] / totalMuestras;
                    actualizadorProgreso.accept(progreso);
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}