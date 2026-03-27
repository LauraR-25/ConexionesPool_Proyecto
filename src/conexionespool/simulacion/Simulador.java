package conexionespool.simulacion;

import conexionespool.componentes.DBComponent;
import conexionespool.componentes.DBComponentRegistry;
import conexionespool.componentes.DBQueryId;
import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.pool.Config;
import conexionespool.util.Freno;
import conexionespool.adaptadores.DatabaseType; // ← import correcto

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Simulador que ejecuta consultas SQL usando componentes de base de datos registrados
public class Simulador {
    private final int totalMuestras;
    private final int reintentosMaximos;
    private final Supplier<DBQueryId> proveedorQueryId;
    private final Freno freno;
    private final AtomicInteger completadas = new AtomicInteger(0);
    private final DatabaseType databaseType;

    public Simulador(int totalMuestras, int reintentosMaximos,
                     Supplier<DBQueryId> proveedorQueryId, Freno freno,
                     DatabaseType databaseType) {
        this.totalMuestras = totalMuestras;
        this.reintentosMaximos = reintentosMaximos;
        this.proveedorQueryId = proveedorQueryId;
        this.freno = freno;
        this.databaseType = databaseType == null ? DatabaseType.POSTGRES : databaseType;
    }

    /**
     * Constructor de compatibilidad: asume POSTGRES.
     */
    public Simulador(int totalMuestras, int reintentosMaximos,
                     Supplier<DBQueryId> proveedorQueryId, Freno freno) {
        this(totalMuestras, reintentosMaximos, proveedorQueryId, freno, DatabaseType.POSTGRES);
    }

    //  Getter para el número de muestras completadas
    public int getCompletadas() {
        return completadas.get();
    }

    public void ejecutarConPool(ContadorEstadisticas contador, Consumer<Double> actualizadorProgreso) {
        ejecutarConPool(contador, actualizadorProgreso, null);
    }

    // Ejecuta
    public void ejecutarConPool(ContadorEstadisticas contador,
                                Consumer<Double> actualizadorProgreso,
                                CountDownLatch inicioCompartido) {
        int poolSize = Math.max(1, Config.getInt("POOL_SIZE"));
        int configuredWorkers = Config.getInt("POOL_WORKERS");
        int workers = configuredWorkers > 0
                ? configuredWorkers
                : Math.max(2, Math.min(poolSize * 2, 64));
        workers = Math.min(workers, Math.max(1, totalMuestras));
        ExecutorService exec = Executors.newFixedThreadPool(workers);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalMuestras);

        for (int i = 0; i < totalMuestras; i++) {
            final int id = i + 1;
            final DBQueryId queryId = proveedorQueryId.get();
            exec.execute(() -> {
                try {
                    startGate.await();
                    if (freno.estaActivado()) return;
                    ejecutarMuestra(id, queryId, contador);
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

    private void ejecutarMuestra(int id, DBQueryId queryId, ContadorEstadisticas contador) {
        boolean exito = false;
        String mensajeError = "";
        int reintentos = 0;

        while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
            try {
                DBComponent comp = DBComponentRegistry.get(databaseType);
                if (comp == null) {
                    throw new IllegalStateException("No hay DBComponent registrado para " + databaseType + ". Conecta primero.");
                }
                comp.query(queryId);
                exito = true;
                break;
            } catch (Exception e) {
                mensajeError = e.getMessage();
                reintentos++;
            }
        }

        Resultado resultado = new Resultado(id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis());
        // SimulationLogger.log(resultado); // Desactivado para evitar lentitud
        contador.registrar(resultado);
    }
}