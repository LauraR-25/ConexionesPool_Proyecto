package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.componentes.DBComponent;
import conexionespool.componentes.DBComponentRegistry;
import conexionespool.componentes.DBQueryId;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Simulador {
    private final int totalMuestras;
    private final int reintentosMaximos;
    private final Supplier<DBQueryId> proveedorQueryId;
    private final DatabaseType freno;
    private final AtomicInteger completadas = new AtomicInteger(0);
    private final Random random = new Random();

    public Simulador(int totalMuestras, int reintentosMaximos,
                     Supplier<DBQueryId> proveedorQueryId, DatabaseType freno) {
        this.totalMuestras = totalMuestras;
        this.reintentosMaximos = reintentosMaximos;
        this.proveedorQueryId = proveedorQueryId;
        this.freno = freno;
    }

    public int getCompletadas() {
        return completadas.get();
    }

    public void ejecutarConPool(ContadorEstadisticas contador, Consumer<Double> actualizadorProgreso) {
        Thread[] hilos = new Thread[totalMuestras];
        for (int i = 0; i < totalMuestras; i++) {
            final int id = i + 1;
            final DBQueryId queryId = proveedorQueryId.get();
            hilos[i] = new Thread(() -> {
                if (freno.estaActivado()) return;
                ejecutarMuestra(id, queryId, contador, actualizadorProgreso);
                completadas.incrementAndGet();
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

    private void ejecutarMuestra(int id, DBQueryId queryId, ContadorEstadisticas contador,
                                 Consumer<Double> actualizador) {
        boolean exito = false;
        String mensajeError = "";
        int reintentos = 0;

        while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
            try {
                DBComponent comp = DBComponentRegistry.get(DatabaseType.POSTGRES);
                comp.query(queryId);
                exito = true;
                break;
            } catch (Exception e) {
                mensajeError = e.getMessage();
                reintentos++;
                // Log opcional (puedes imprimir en consola si quieres)
                // System.out.println("❌ Pool - Error en petición " + id + " (reintento " + reintentos + "): " + mensajeError);
            }
        }

        Resultado resultado = new Resultado(id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis());
        contador.registrar(resultado);
        actualizador.accept((double) id / totalMuestras);
    }
}