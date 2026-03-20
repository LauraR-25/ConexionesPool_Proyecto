package conexionespool.adaptadores;

public class H2Adapter implements IDBAdapter {

    @Override
    public DatabaseType type() {
        return DatabaseType.H2;
    }

    @Override
    public String driverClassName() {
        return "org.h2.Driver";
    }

    @Override
    public String buildJdbcUrl(String host, int port, String dbName) {
        // H2 en modo embebido, crea un archivo en ./databases/<dbName>
        return "jdbc:h2:./databases/" + dbName;
    }

    @Override
    public String queriesResource() {
        return "db/queries-h2.properties";
    }
}