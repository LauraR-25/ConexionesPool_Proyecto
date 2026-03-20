package conexionespool.adaptadores;

public interface IDBAdapter {
    DatabaseType type();
    String driverClassName();
    String buildJdbcUrl(String host, int port, String dbName);
    String queriesResource(); // ruta del archivo de queries (classpath)
}