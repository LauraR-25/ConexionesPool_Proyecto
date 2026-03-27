package conexionespool.componentes;

import conexionespool.adaptadores.DatabaseType;
import java.util.concurrent.ConcurrentHashMap;

// Registro centralizado de componentes de base de datos
public final class DBComponentRegistry {
    private static final ConcurrentHashMap<DatabaseType, DBComponent> COMPONENTS = new ConcurrentHashMap<>();

    private DBComponentRegistry() {}

    // Registra un componente para un tipo de base de datos
    public static void put(DatabaseType type, DBComponent component) {
        if (type == null) throw new IllegalArgumentException("type no puede ser null");
        if (component == null) throw new IllegalArgumentException("component no puede ser null");
        COMPONENTS.put(type, component);
    }

    // Registra un componente reemplazando el anterior y desconectando la conexión previa si existe
    public static void putReplacing(DatabaseType type, DBComponent component) {
        if (type == null) throw new IllegalArgumentException("type no puede ser null");
        if (component == null) throw new IllegalArgumentException("component no puede ser null");

        DBComponent previous = COMPONENTS.put(type, component);
        if (previous != null && previous != component) {
            try { previous.disconnect(); } catch (Exception ignored) {}
        }
    }

    // Devuelve null si no hay componente registrado para el tipo dado
    public static DBComponent get(DatabaseType type) {
        return COMPONENTS.get(type);
    }

    // Elimina el componente registrado y desconecta la conexión si existe
    public static void clear(DatabaseType type) {
        if (type == null) throw new IllegalArgumentException("type no puede ser null");
        DBComponent removed = COMPONENTS.remove(type);
        if (removed != null) {
            try { removed.disconnect(); } catch (Exception ignored) {}
        }
    }

    // Devuelve false si no hay componente registrado o el componente no está conectado
    public static boolean isConnected(DatabaseType type) {
        DBComponent c = COMPONENTS.get(type);
        return c != null && c.isConnected();
    }
}