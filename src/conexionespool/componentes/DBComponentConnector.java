package conexionespool.componentes;

import conexionespool.util.DatabaseType;

/**
 * Servicio de conexión para desacoplar la UI de la lógica de creación del DBComponent.
 */
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

        // Para simplificar, solo PostgreSQL. En un futuro podrías tener un adaptador.
        String driverClassName = "org.postgresql.Driver";
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        String queriesLocation = toClasspathLocation("db/queries-postgres.properties");

        DBComponent component = new DBComponent(
                driverClassName,
                url,
                user,
                password,
                queriesLocation
        );

        // Verificación temprana de conectividad y queries predefinidas.
        component.query(DEFAULT_PING_QUERY);

        return new ConnectResult(type, new ConnectionConfig(driverClassName, url, user, password), queriesLocation, component);
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