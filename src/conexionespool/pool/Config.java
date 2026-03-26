package conexionespool.pool;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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

    public static String get(String key) {
        return properties.getProperty(key);
    }

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