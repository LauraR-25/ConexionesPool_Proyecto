package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.util.Freno;
import conexionespool.util.LoggerMuestras;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class Simulador {
    protected final int totalMuestras;
    protected final int reintentosMaximos;
    protected final Supplier<String> proveedorQuery;
    protected final Freno freno;
    protected final LoggerMuestras logger;
    protected final Random random = new Random();

    public Simulador(int totalMuestras, int reintentosMaximos, Supplier<String> proveedorQuery, Freno freno, LoggerMuestras logger) {
        this.totalMuestras = totalMuestras;
        this.reintentosMaximos = reintentosMaximos;
        this.proveedorQuery = proveedorQuery;
        this.freno = freno;
        this.logger = logger;
    }

    protected abstract void ejecutarMuestra(int id, String query, ContadorEstadisticas contador, Consumer<Double> actualizador) throws Exception;

    public void ejecutar(ContadorEstadisticas contador, Consumer<Double> actualizadorProgreso) {
        Thread[] hilos = new Thread[totalMuestras];

        for (int i = 0; i < totalMuestras; i++) {
            final int id = i + 1;
            final String query = proveedorQuery.get();
            hilos[i] = new Thread(() -> {
                if (freno.estaActivado()) return;
                try {
                    ejecutarMuestra(id, query, contador, actualizadorProgreso);
                } catch (Exception e) {
                    logger.log("Error en muestra " + id + ": " + e.getMessage());
                }
            });
            hilos[i].start();
        }

        for (Thread h : hilos) {
            try { h.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}