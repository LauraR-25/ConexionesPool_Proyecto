package conexionespool.adaptadores;

public class MySQLAdapter implements IDBAdapter {

    @Override
    public DatabaseType type() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String driverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public String buildJdbcUrl(String host, int port, String dbName) {
        return "jdbc:mysql://" + host + ":" + port + "/" + dbName;
    }

    @Override
    public String queriesResource() {
        return "db/queries-mysql.properties";
    }
}