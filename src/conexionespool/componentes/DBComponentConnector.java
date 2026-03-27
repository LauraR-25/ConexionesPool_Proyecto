package conexionespool.componentes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import conexionespool.adaptadores.DatabaseType;
import conexionespool.adaptadores.IDBAdapter;
import conexionespool.adaptadores.PostgreSQLAdapter;
import conexionespool.adaptadores.MySQLAdapter;
import conexionespool.adaptadores.H2Adapter;

public final class DBComponentConnector {

    // El constructor es privado porque esta clase solo expone métodos estáticos.
    public ConnectResult connect(DatabaseType type,
                                 String host,
                                 int port,
                                 String dbName,
                                 String user,
                                 String password) throws DBException {
        if (type == null) {
            throw new DBException(DBException.Category.CONFIG, null, "DatabaseType no puede ser null");
        }

        IDBAdapter adapter;
        switch (type) {
            case POSTGRES:
                adapter = new PostgreSQLAdapter();
                break;
            case MYSQL:
                adapter = new MySQLAdapter();
                break;
            case H2:
                adapter = new H2Adapter();
                break;
            default:
                throw new DBException(DBException.Category.CONFIG, null,
                        "Tipo de base de datos no soportado: " + type);
        }

        String url = adapter.buildJdbcUrl(host, port, dbName);
        String queriesLocation = toClasspathLocation(adapter.queriesResource());

        // Carga temprana del driver
        try {
            Class.forName(adapter.driverClassName());
        } catch (ClassNotFoundException e) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "No se encontró el driver JDBC en el classpath: " + adapter.driverClassName() +
                            ". Asegúrate de tener el .jar en lib/ y agregado como dependencia.", e);
        }

        ConnectionTarget target = resolveAndPing(type, url, user, password, dbName);

        DBComponent component = new DBComponent(
                adapter.driverClassName(),
                target.url(),
                target.user(),
                target.password(),
                queriesLocation
        );


        return new ConnectResult(type, new ConnectionConfig(adapter.driverClassName(), target.url(), target.user(), target.password()), queriesLocation, component);
    }

    // Para H2, intentamos varias combinaciones de credenciales y URLs (incluyendo memoria) para asegurar que se pueda conectar
    private ConnectionTarget resolveAndPing(DatabaseType type, String url, String user, String password, String dbName) throws DBException {
        if (type != DatabaseType.H2) {
            ping(url, user, password, type);
            return new ConnectionTarget(url, user, password);
        }

        Credentials[] candidates = new Credentials[] {
                new Credentials(user, password),
                new Credentials("sa", ""),
                new Credentials("", "")
        };

        DBException last = null;
        for (Credentials candidate : candidates) {
            try {
                ping(url, candidate.user(), candidate.password(), type);
                return new ConnectionTarget(url, candidate.user(), candidate.password());
            } catch (DBException e) {
                last = e;
            }
        }

        //  Si no funcionó con la URL normal, intentamos con una base de datos en memoria, que es común para H2 y no requiere configuración previa.
        String safeDbName = (dbName == null || dbName.isBlank()) ? "simulacion" : dbName.trim();
        String memoryUrl = "jdbc:h2:mem:" + safeDbName + ";DB_CLOSE_DELAY=-1";
        for (Credentials candidate : candidates) {
            try {
                ping(memoryUrl, candidate.user(), candidate.password(), type);
                return new ConnectionTarget(memoryUrl, candidate.user(), candidate.password());
            } catch (DBException e) {
                last = e;
            }
        }

        throw last == null
                ? new DBException(DBException.Category.CONNECTION, null, "No se pudo validar conexión H2")
                : last;
    }

    private void ping(String url, String user, String password, DatabaseType type) throws DBException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             Statement st = c.createStatement()) {
            st.setQueryTimeout(5);
            st.execute("SELECT 1");
            ensureMinimalSchema(type, st);
        } catch (SQLException e) {
            throw DBException.fromSQLException(e, "connect.ping");
        }
    }

    // Clases auxiliares para manejar combinaciones de credenciales y resultados de conexión de forma más clara.
    private record Credentials(String user, String password) {}
    private record ConnectionTarget(String url, String user, String password) {}

    private void ensureMinimalSchema(DatabaseType type, Statement st) throws SQLException {
        if (type == DatabaseType.POSTGRES) {
            return;
        }

        switch (type) {
            case MYSQL -> st.execute("CREATE TABLE IF NOT EXISTS usuario (id INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100))");
            case H2 -> st.execute("CREATE TABLE IF NOT EXISTS usuario (id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, nombre VARCHAR(100))");
            default -> {
                return;
            }
        }

        try (var rs = st.executeQuery("SELECT COUNT(*) FROM usuario")) {
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO usuario(nombre) VALUES ('demo')");
            }
        }
    }

    // Convierte una ruta de recurso relativa a una ubicación de classpath
    private String toClasspathLocation(String adapterResource) {
        if (adapterResource == null || adapterResource.isBlank()) {
            throw new IllegalArgumentException("queriesResource no puede ser null/vacío");
        }
        String normalized = adapterResource.startsWith("/") ? adapterResource : "/" + adapterResource;
        return "classpath:" + normalized;
    }

    // El resultado de la conexión incluye toda la información relevante para crear el componente y usarlo
    public record ConnectResult(
            DatabaseType type,
            ConnectionConfig config,
            String queriesLocation,
            DBComponent component
    ) {
    }
}