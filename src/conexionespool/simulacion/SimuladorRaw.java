package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.util.Freno;
import conexionespool.util.LoggerMuestras;

import java.sql.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimuladorRaw {
    private final int totalMuestras;
    private final int reintentosMaximos;
    private final Supplier<String> proveedorQuery;
    private final Freno freno;
    private final LoggerMuestras logger;
    private final Random random = new Random();
    private final String url, user, pass;

    public SimuladorRaw(int totalMuestras, int reintentosMaximos, Supplier<String> proveedorQuery,
                        Freno freno, LoggerMuestras logger,
                        String url, String user, String pass) {
        this.totalMuestras = totalMuestras;
        this.reintentosMaximos = reintentosMaximos;
        this.proveedorQuery = proveedorQuery;
        this.freno = freno;
        this.logger = logger;
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    public void ejecutar(ContadorEstadisticas contador, Consumer<Double> actualizadorProgreso) {
        ExecutorService executor = Executors.newFixedThreadPool(200); // Máximo 200 hilos concurrentes
        for (int i = 0; i < totalMuestras; i++) {
            final int id = i + 1;
            final String query = proveedorQuery.get();
            executor.submit(() -> {
                if (freno.estaActivado()) return;
                ejecutarMuestra(id, query, contador, actualizadorProgreso);
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ejecutarMuestra(int id, String query, ContadorEstadisticas contador, Consumer<Double> actualizador) {
        boolean exito = false;
        String mensajeError = "";
        int reintentos = 0;

        while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
            if (reintentos > 0) {
                try { Thread.sleep(random.nextInt(50)); } catch (InterruptedException e) { break; }
            }

            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery(query);
                    if (rs.next()) exito = true;
                    rs.close();
                }
                break;
            } catch (SQLException e) {
                mensajeError = e.getMessage();
                reintentos++;
                logger.log(String.format("Raw %d reintento %d: %s", id, reintentos, mensajeError));
            }
        }

        Resultado resultado = new Resultado(id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis());
        contador.registrar(resultado);
        logger.log(resultado.formatoLog());
        actualizador.accept((double) id / totalMuestras);
    }
}