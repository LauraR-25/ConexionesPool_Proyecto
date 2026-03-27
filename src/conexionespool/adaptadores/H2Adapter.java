package conexionespool.adaptadores;

// Adaptador específico para H2, una base de datos en memoria o embebida
public class H2Adapter implements IDBAdapter {

    @Override
    // H2 es una base de datos en memoria o embebida, no requiere host ni puerto
    public DatabaseType type() {
        return DatabaseType.H2;
    }

    @Override
    public String driverClassName() {
        return "org.h2.Driver";
    }

    @Override
    // Para H2, la URL se construye con el formato "jdbc:h2:./databases/<dbName>"
    public String buildJdbcUrl(String host, int port, String dbName) {
        // H2 en modo embebido, crea un archivo en ./databases/<dbName>
        return "jdbc:h2:./databases/" + dbName;
    }

    @Override
    public String queriesResource() {
        return "db/queries-h2.properties";
    }
}