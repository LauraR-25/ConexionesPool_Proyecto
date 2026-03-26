package conexionespool.simulacion;

import conexionespool.adaptadores.DatabaseType;
import conexionespool.componentes.DBComponentConnector;
import conexionespool.componentes.DBQueryId;
import conexionespool.util.ConfiguracionEntorno;

public final class MotorSmokeCheck {
    public static void main(String[] args) {
        checkPostgres();
        checkH2();
        checkMySql();
    }

    private static void checkPostgres() {
        ConfiguracionEntorno env = new ConfiguracionEntorno(".env");
        String host = valueOrDefault(env.obtener("DB_HOST"), "localhost");
        int port = intOrDefault(env.obtener("DB_PORT"), 5432);
        String db = valueOrDefault(env.obtener("DB_NAME"), "javaprueba");
        String user = valueOrDefault(env.obtener("DB_USER"), "postgres");
        String pass = valueOrDefault(env.obtener("DB_PASSWORD"), "postgres");
        runCheck("POSTGRES", DatabaseType.POSTGRES, host, port, db, user, pass);
    }

    private static void checkH2() {
        ConfiguracionEntorno env = new ConfiguracionEntorno(".env");
        String db = valueOrDefault(env.obtener("H2_DB"), valueOrDefault(env.obtener("DB_NAME"), "javaprueba"));
        String user = valueOrDefault(env.obtener("H2_USER"), "sa");
        String pass = valueOrDefault(env.obtener("H2_PASSWORD"), "");
        runCheck("H2", DatabaseType.H2, "", 0, db, user, pass);
    }

    private static void checkMySql() {
        ConfiguracionEntorno env = new ConfiguracionEntorno(".env");
        String host = valueOrDefault(env.obtener("MYSQL_HOST"), "localhost");
        int port = intOrDefault(env.obtener("MYSQL_PORT"), 3306);
        String db = valueOrDefault(env.obtener("MYSQL_DB"), "javaprueba");
        String user = valueOrDefault(env.obtener("MYSQL_USER"), "root");
        String pass = valueOrDefault(env.obtener("MYSQL_PASSWORD"), "root");
        runCheck("MYSQL", DatabaseType.MYSQL, host, port, db, user, pass);
    }

    private static void runCheck(String label,
                                 DatabaseType type,
                                 String host,
                                 int port,
                                 String dbName,
                                 String user,
                                 String password) {
        DBComponentConnector connector = new DBComponentConnector();
        try {
            DBComponentConnector.ConnectResult result = connector.connect(type, host, port, dbName, user, password);
            try {
                result.component().query(new DBQueryId("usuario.selectOne"));
                System.out.println(label + " PASS");
            } finally {
                result.component().disconnect();
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            System.out.println(label + " FAIL - " + msg);
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static int intOrDefault(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
