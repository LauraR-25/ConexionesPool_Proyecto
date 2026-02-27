package conexionespool.modelo;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ContadorEstadisticas implements Runnable {
    private final BlockingQueue<Resultado> cola;
    private volatile boolean activo = true;
    private final AtomicInteger exitosas = new AtomicInteger(0);
    private final AtomicInteger fallidas = new AtomicInteger(0);
    private final AtomicLong totalReintentos = new AtomicLong(0);
    private int totalProcesadas = 0;

    public ContadorEstadisticas() {
        this.cola = new LinkedBlockingQueue<>();
    }

    public void registrar(Resultado resultado) {
        cola.offer(resultado);
    }

    @Override
    public void run() {
        while (activo || !cola.isEmpty()) {
            try {
                Resultado r = cola.poll();
                if (r != null) {
                    totalProcesadas++;
                    if (r.isExitosa()) {
                        exitosas.incrementAndGet();
                    } else {
                        fallidas.incrementAndGet();
                    }
                    totalReintentos.addAndGet(r.getReintentos());
                } else {
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void detener() { activo = false; }
    public int getExitosas() { return exitosas.get(); }
    public int getFallidas() { return fallidas.get(); }
    public double getPromedioReintentos() {
        return totalProcesadas == 0 ? 0 : totalReintentos.get() / (double) totalProcesadas;
    }
    public double getPorcentajeExito() {
        return totalProcesadas == 0 ? 0 : exitosas.get() * 100.0 / totalProcesadas;
    }
    public double getPorcentajeFallo() {
        return totalProcesadas == 0 ? 0 : fallidas.get() * 100.0 / totalProcesadas;
    }
}