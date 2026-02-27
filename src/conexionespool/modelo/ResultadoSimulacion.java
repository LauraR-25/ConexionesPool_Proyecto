package conexionespool.modelo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ResultadoSimulacion {
    private final String nombre;
    private final long tiempoMs;
    private final List<Muestra> muestras;
    private final int totalReintentos;

    public ResultadoSimulacion(String nombre, long tiempoMs, List<Muestra> muestras, int totalReintentos) {
        this.nombre = nombre;
        this.tiempoMs = tiempoMs;
        this.muestras = muestras;
        this.totalReintentos = totalReintentos;
    }

    public String getNombre() { return nombre; }
    public long getTiempoMs() { return tiempoMs; }
    public List<Muestra> getMuestras() { return muestras; }
    public int getTotalReintentos() { return totalReintentos; }

    public int getExitosas() {
        return (int) muestras.stream().filter(Muestra::isExitosa).count();
    }

    public int getFallidas() {
        return muestras.size() - getExitosas();
    }

    public double getPorcentajeExito() {
        return muestras.isEmpty() ? 0 : getExitosas() * 100.0 / muestras.size();
    }

    public double getPromedioReintentos() {
        return muestras.isEmpty() ? 0 : (double) totalReintentos / muestras.size();
    }
}