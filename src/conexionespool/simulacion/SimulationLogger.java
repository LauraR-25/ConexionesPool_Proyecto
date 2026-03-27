package conexionespool.simulacion;

import conexionespool.modelo.Resultado;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.locks.ReentrantLock;

// Clase para manejar el logging de la simulación
public class SimulationLogger {
    private static final String LOG_FILE = "simulacion.log";
    private static final ReentrantLock lock = new ReentrantLock();

    // Metodo para escribir un resultado en el log
    public static void log(Resultado resultado) {
        lock.lock();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)))) {
            out.println(resultado.formatoLog());
        } catch (IOException e) {
            System.err.println("[Logger] Error escribiendo en simulacion.log: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // "" una línea en el log
    public static void logLinea(String linea) {
        lock.lock();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)))) {
            out.println(linea);
        } catch (IOException e) {
            System.err.println("[Logger] Error escribiendo en simulacion.log: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // "" limpiar el log (sobrescribe el archivo)
    public static void limpiarLog() {
        lock.lock();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, false)))) {
            // Limpia el archivo
        } catch (IOException e) {
            System.err.println("[Logger] Error limpiando simulacion.log: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
}

