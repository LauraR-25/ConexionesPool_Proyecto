package conexionespool.componentes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import conexionespool.adaptadores.DatabaseType;
import conexionespool.adaptadores.IDBAdapter;
import conexionespool.adaptadores.PostgreSQLAdapter;
import conexionespool.adaptadores.MySQLAdapter;
import conexionespool.adaptadores.H2Adapter;  // importar el nuevo adaptador

public final class DBComponentConnector {

    private static final DBQueryId DEFAULT_PING_QUERY = new DBQueryId("usuario.selectOne");

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

        DBComponent component = new DBComponent(
                adapter.driverClassName(),
                url,
                user,
                password,
                queriesLocation
        );

        // Verificación temprana de conectividad
        component.query(DEFAULT_PING_QUERY);

        return new ConnectResult(type, new ConnectionConfig(adapter.driverClassName(), url, user, password), queriesLocation, component);
    }

    private String toClasspathLocation(String adapterResource) {
        if (adapterResource == null || adapterResource.isBlank()) {
            throw new IllegalArgumentException("queriesResource no puede ser null/vacío");
        }
        String normalized = adapterResource.startsWith("/") ? adapterResource : "/" + adapterResource;
        return "classpath:" + normalized;
    }

    public record ConnectResult(
            DatabaseType type,
            ConnectionConfig config,
            String queriesLocation,
            DBComponent component
    ) {
    }
}