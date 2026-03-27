package conexionespool.adaptadores;

public interface IDBAdapter {
    DatabaseType type();
    String driverClassName();
    String buildJdbcUrl(String host, int port, String dbName);
    String queriesResource(); // ruta del archivo de queries (classpath)
}
// Define el contrato que cada adaptador específico
// (PostgreSQL, MySQL, H2) debe cumplir para ser utilizado por el DBComponentConnector.
// Cada adaptador proporciona la información necesaria para construir la URL JDBC, cargar el driver y
// localizar los queries específicos de esa base de datos