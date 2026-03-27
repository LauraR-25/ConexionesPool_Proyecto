package conexionespool.pool;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

//Clase para cargar configuración de la aplicación desde un archivo .env
public class Config {
    private static final Properties properties = new Properties();

    static {
        // Intentamos cargar .env si existe. Si no, seguimos con defaults.
        try (var fis = new FileInputStream(".env")) {
            properties.load(fis);
        } catch (IOException ignored) {
            // Sin .env: usar defaults
        }
    }

    // Obtiene un valor de configuración por clave desde el archivo .env (si existe).
    public static String get(String key) {
        return properties.getProperty(key);
    }

    // Obtiene un valor entero de configuración, aplicando defaults si la clave no está definida.
    public static int getInt(String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return switch (key) {
                case "POOL_SIZE" -> 10;
                default -> 0;
            };
        }
        return Integer.parseInt(raw.trim());
    }

    // Obtiene un valor long de configuración, aplicando defaults si la clave no está definida.
    public static long getLong(String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return switch (key) {
                case "POOL_TIMEOUT" -> 30_000L;
                default -> 0L;
            };
        }
        return Long.parseLong(raw.trim());
    }
}