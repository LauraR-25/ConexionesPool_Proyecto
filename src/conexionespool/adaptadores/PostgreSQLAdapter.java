package conexionespool.adaptadores;

// Implementación del adaptador para PostgreSQL
public class PostgreSQLAdapter implements IDBAdapter {

    @Override
    public DatabaseType type() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public String driverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String buildJdbcUrl(String host, int port, String dbName) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
    }

    @Override
    public String queriesResource() {
        return "db/queries-postgres.properties";
    }
}