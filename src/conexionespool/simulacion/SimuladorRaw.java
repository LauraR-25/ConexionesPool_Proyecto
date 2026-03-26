package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.pool.Config;
import conexionespool.util.Freno;

import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimuladorRaw {
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 10;
    private final int totalMuestras;
    private final int reintentosMaximos;
    private final Supplier<String> proveedorQuery;
    private final Freno freno;
    private final String url, user, pass;
    private final AtomicInteger completadas = new AtomicInteger(0);
    private final Semaphore slotsConexion;
    private final long timeoutSlotMs;

    public SimuladorRaw(int totalMuestras, int reintentosMaximos, Supplier<String> proveedorQuery,
                        Freno freno, String url, String user, String pass) {
        this.totalMuestras = totalMuestras;
        this.reintentosMaximos = reintentosMaximos;
        this.proveedorQuery = proveedorQuery;
        this.freno = freno;
        this.url = url;
        this.user = user;
        this.pass = pass;
        int maxActiveConnections = Config.getInt("RAW_MAX_ACTIVE_CONNECTIONS");
        if (maxActiveConnections <= 0) {
            maxActiveConnections = 24;
        }
        this.slotsConexion = new Semaphore(maxActiveConnections);
        long slotTimeout = Config.getLong("RAW_ACQUIRE_TIMEOUT_MS");
        this.timeoutSlotMs = slotTimeout > 0 ? slotTimeout : 20L;

        preloadDriverForUrl(url);
    }

    private void preloadDriverForUrl(String jdbcUrl) {
        String driver = null;
        if (jdbcUrl != null) {
            String lower = jdbcUrl.toLowerCase();
            if (lower.startsWith("jdbc:postgresql:")) {
                driver = "org.postgresql.Driver";
            } else if (lower.startsWith("jdbc:mysql:")) {
                driver = "com.mysql.cj.jdbc.Driver";
            } else if (lower.startsWith("jdbc:h2:")) {
                driver = "org.h2.Driver";
            }
        }
        if (driver == null) {
            return;
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException ignored) {
            // El error real aparecerá al abrir la conexión en ejecutarMuestra.
        }
    }

    public int getCompletadas() {
        return completadas.get();
    }

    public void ejecutar(ContadorEstadisticas contador, Consumer<Double> actualizadorProgreso) {
        ejecutar(contador, actualizadorProgreso, null);
    }

    public void ejecutar(ContadorEstadisticas contador,
                        Consumer<Double> actualizadorProgreso,
                        CountDownLatch inicioCompartido) {
        int configuredWorkers = Config.getInt("RAW_WORKERS");
        int workers = configuredWorkers > 0
                ? configuredWorkers
                : Math.max(64, Math.min(300, totalMuestras));
        workers = Math.min(workers, Math.max(1, totalMuestras));
        ExecutorService exec = Executors.newFixedThreadPool(workers);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalMuestras);

        for (int i = 0; i < totalMuestras; i++) {
            final int id = i + 1;
            final String query = proveedorQuery.get();
            exec.execute(() -> {
                try {
                    startGate.await();
                    if (freno.estaActivado()) return;
                    ejecutarMuestra(id, query, contador);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    int done = completadas.incrementAndGet();
                    actualizadorProgreso.accept(done / (double) totalMuestras);
                    doneGate.countDown();
                }
            });
        }

        if (inicioCompartido != null) {
            try {
                inicioCompartido.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        startGate.countDown();
        try {
            doneGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exec.shutdownNow();
        }
    }

    private int statementTimeoutSeconds() {
        int configured = Config.getInt("QUERY_TIMEOUT_SECONDS");
        return configured > 0 ? configured : DEFAULT_QUERY_TIMEOUT_SECONDS;
    }

    private void ejecutarMuestra(int id, String query, ContadorEstadisticas contador) {
        boolean exito = false;
        String mensajeError = "";
        int reintentos = 0;

        while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
            boolean slotTomado = false;
            try {
                slotTomado = slotsConexion.tryAcquire(timeoutSlotMs, TimeUnit.MILLISECONDS);
                if (!slotTomado) {
                    mensajeError = "Saturacion raw: no se pudo abrir conexion a tiempo";
                    reintentos++;
                    continue;
                }
                try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.setQueryTimeout(statementTimeoutSeconds());
                        try (ResultSet rs = stmt.executeQuery(query)) {
                            // Éxito = la query se ejecutó sin excepción.
                            exito = true;
                        }
                    }
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mensajeError = "Interrumpido";
                reintentos++;
            } catch (SQLException e) {
                mensajeError = e.getMessage();
                reintentos++;
                // Log opcional
                // System.out.println("❌ Raw - Error en petición " + id + " (reintento " + reintentos + "): " + mensajeError);
            } finally {
                if (slotTomado) {
                    slotsConexion.release();
                }
            }
        }

        Resultado resultado = new Resultado(id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis());
        // SimulationLogger.log(resultado); // Desactivado para evitar lentitud
        contador.registrar(resultado);
    }
}