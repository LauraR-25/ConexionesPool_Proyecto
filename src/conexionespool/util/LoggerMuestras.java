package conexionespool.util;

public class LoggerMuestras {
    public void log(String mensaje) {
        // Delegar en el logger general que escribe en archivo
        LoggerSimulacion.log(mensaje);
    }
}