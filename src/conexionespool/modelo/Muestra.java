package conexionespool.modelo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Muestra {
    private final int id;
    private final boolean exitosa;
    private final int reintentos;
    private final LocalDateTime timestamp;
    private final String query;
    private final String error;

    public Muestra(int id, boolean exitosa, int reintentos, String query, String error) {
        this.id = id;
        this.exitosa = exitosa;
        this.reintentos = reintentos;
        this.timestamp = LocalDateTime.now();
        this.query = query;
        this.error = error;
    }

    public int getId() { return id; }
    public boolean isExitosa() { return exitosa; }
    public int getReintentos() { return reintentos; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getQuery() { return query; }
    public String getError() { return error; }

    public String toLogLine() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return String.format("[%s] Muestra %d: %s | reintentos: %d | query: %s | %s",
                timestamp.format(fmt), id, exitosa ? "EXITOSA" : "FALLIDA", reintentos, query,
                exitosa ? "" : "error: " + error);
    }
}