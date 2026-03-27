package conexionespool.pool;

import java.sql.Connection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class PoolManager {
    private static final ConcurrentHashMap<String, PoolManager> INSTANCES = new ConcurrentHashMap<>();
    private final String instanceKey;
    private final ConnectionPool pool;

    // Construye un PoolManager con un pool interno para una configuración de conexión específica.
    private PoolManager(String driverClassName, String url, String user, String password) {
        this.instanceKey = driverClassName + "|" + url + "|" + user;
        try {
            pool = new Pool(driverClassName, url, user, password, Config.getInt("POOL_SIZE"));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo inicializar el pool de conexiones", e);
        }
    }

    // Obtiene o crea una instancia singleton de PoolManager por combinación driver/url/usuario.
    public static PoolManager getInstance(String driverClassName, String url, String user, String password) {
        Objects.requireNonNull(driverClassName, "driverClassName");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");

        String key = driverClassName + "|" + url + "|" + user;
        return INSTANCES.computeIfAbsent(key, _k -> new PoolManager(driverClassName, url, user, password));
    }

    // Obtiene la primera instancia ya inicializada de PoolManager (falla si no se inicializó antes).
    public static synchronized PoolManager getInstance() {
        return INSTANCES.values().stream().findFirst().orElseGet(() -> {
            throw new IllegalStateException("PoolManager no ha sido inicializado. Usa getInstance(driver,url,user,password) primero.");
        });
    }

    // Solicita una conexión al pool manejando interrupciones del hilo.
    public Connection getConnection() {
        try {
            return pool.getConnection();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // Devuelve una conexión al pool para que sea reutilizada.
    public void releaseConnection(Connection connection) {
        pool.releaseConnection(connection);
    }

    // Cierra el pool subyacente y elimina esta instancia del registro de singletons.
    public void close() throws Exception {
        if (pool instanceof Pool p) {
            p.closePool();
        }
        INSTANCES.remove(instanceKey, this);
    }
}