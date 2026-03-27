package conexionespool.modelo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

//Model para representar el resultado de una petición a la base de datos, incluyendo estado, mensaje y número de reintentos
public class Resultado {
    private final int id;
    private final boolean exitosa;
    private final String mensaje;
    private final int reintentos;
    private final long timestamp;

    public Resultado(int id, boolean exitosa, String mensaje, int reintentos, long timestamp) {
        this.id = id;
        this.exitosa = exitosa;
        this.mensaje = mensaje;
        this.reintentos = reintentos;
        this.timestamp = timestamp;
    }

    // Getters para acceder a los campos del resultado
    public int getId() { return id; }
    public boolean isExitosa() { return exitosa; }
    public String getMensaje() { return mensaje; }
    public int getReintentos() { return reintentos; }
    public long getTimestamp() { return timestamp; }

    //Formatea el resultado en una cadena legible para los logs, incluyendo la hora de la petición y estado
    public String formatoLog() {
        LocalDateTime fecha = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        String hora = fecha.format(DateTimeFormatter.ISO_LOCAL_TIME);
        return String.format("[%s] Petición %d: %s (reintentos: %d) – %s",
                hora, id, exitosa ? "EXITOSA" : "FALLIDA", reintentos, mensaje);
    }
}