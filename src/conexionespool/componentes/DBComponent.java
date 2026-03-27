package conexionespool.componentes;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import conexionespool.pool.Config;
import conexionespool.pool.PoolManager;


// Componente de acceso a base de datos con pool de conexiones y manejo de queries predefinidas. </summary>
public final class DBComponent implements DBConnection {
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 10;
    private final PoolManager poolManager;
    private final DBQueries queries;
    private final String queriesLocation;
    private final boolean ownsPool;

    private final String url;
    private final String user;
    private final String password;
    private volatile boolean connected;

    /**
     * Crea el componente y construye su pool interno automáticamente.
     */
    public DBComponent(String driverClassName,
                       String url,
                       String user,
                       String password,
                       String queriesLocation) throws DBException {
        this(driverClassName, url, user, password, queriesLocation, null);
    }

    /**
     * Crea el componente permitiendo inyectar un pool externo.
     * Si poolManager es null, se crea/obtiene uno por configuración.
     */
    public DBComponent(String driverClassName,
                       String url,
                       String user,
                       String password,
                       String queriesLocation,
                       PoolManager poolManager) throws DBException {
        Objects.requireNonNull(driverClassName, "driverClassName");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(queriesLocation, "queriesLocation");

        this.url = url;
        this.user = user;
        this.password = password;
        this.queriesLocation = queriesLocation;
        this.ownsPool = (poolManager == null);
        this.poolManager = (poolManager != null)
                ? poolManager
                : PoolManager.getInstance(driverClassName, url, user, password);
        this.queries = DBQueries.load(queriesLocation);
        this.connected = true;
    }

    /**
     * Datos de conexión (para reutilizar en modo sin-pool con DriverManager).
     */
    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getQueriesLocation() {
        return queriesLocation;
    }

    /**
     * Compatibilidad con código existente.
     */
    @Deprecated
    // Devuelve ubicación del recurso de queries, por compatibilidad con código
    public String getQueriesResource() {
        return queriesLocation;
    }


    // Obtiene conexión del pool, asegura que el componente esté conectado
    private Connection acquire() throws DBException {
        ensureConnected();
        Connection c = poolManager.getConnection();
        if (c == null) {
            throw new DBException(DBException.Category.TIMEOUT, null,
                    "No se pudo obtener una conexión del pool (interrumpido/timeout)");
        }
        return c;
    }

    // Verifica que el componente esté conectado antes de ejecutar operaciones
    private void ensureConnected() throws DBException {
        if (!connected) {
            throw new DBException(DBException.Category.CONNECTION, null,
                    "DBComponent está desconectado. Llama connect() antes de ejecutar operaciones.");
        }
    }

    // Configura el timeout de las sentencias SQL según la configuración
    private int statementTimeoutSeconds() {
        int configured = Config.getInt("QUERY_TIMEOUT_SECONDS");
        return configured > 0 ? configured : DEFAULT_QUERY_TIMEOUT_SECONDS;
    }

    // Implementación de DBConnection: connect, disconnect, isConnected
    @Override
    public synchronized void connect() {
        connected = true;
    }

    @Override
    public synchronized void disconnect() throws DBException {
        connected = false;
        if (!ownsPool) {
            return;
        }
        try {
            poolManager.close();
        } catch (Exception e) {
            throw new DBException(DBException.Category.IO, null,
                    "Error cerrando pool del DBComponent", e);
        }
    }

    @Override
    // Devuelve el estado de conexión del componente
    public boolean isConnected() {
        return connected;
    }

    // Ejecuta una query predefinida por ID y devuelve los resultados como una lista de filas
    public DBQueryResult<List<Object[]>> query(DBQueryId id) throws DBException {
        String sql = queries.sql(id);
        Connection c = acquire();
        try (Statement st = c.createStatement()) {
            st.setQueryTimeout(statementTimeoutSeconds());
            try (ResultSet rs = st.executeQuery(sql)) {
            var rows = new ArrayList<Object[]>();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 1; i <= cols; i++) row[i - 1] = rs.getObject(i);
                rows.add(row);
            }
            return new DBQueryResult<>(rows, 0);
            }
        } catch (SQLException e) {
            throw DBException.fromSQLException(e, "query(" + id + ")");
        } finally {
            poolManager.releaseConnection(c);
        }
    }

    // " " y mapea cada fila usando el RowMapper proporcionado.
    public <R> DBQueryResult<List<R>> query(DBQueryId id, RowMapper<R> mapper) throws DBException {
        Objects.requireNonNull(mapper, "mapper");
        String sql = queries.sql(id);

        Connection c = acquire();
        try (Statement st = c.createStatement()) {
            st.setQueryTimeout(statementTimeoutSeconds());
            try (ResultSet rs = st.executeQuery(sql)) {
            List<R> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapper.mapRow(rs));
            }
            return new DBQueryResult<>(out, 0);
            }
        } catch (SQLException e) {
            throw DBException.fromSQLException(e, "query(" + id + ", mapper)");
        } finally {
            poolManager.releaseConnection(c);
        }
    }

    // Ejecuta una query de actualización (INSERT/UPDATE/DELETE) predefinida por ID y devuelve el número de filas afectadas.
    public DBQueryResult<Void> update(DBQueryId id) throws DBException {
        String sql = queries.sql(id);
        Connection c = acquire();
        try (Statement st = c.createStatement()) {
            st.setQueryTimeout(statementTimeoutSeconds());
            int affected = st.executeUpdate(sql);
            return new DBQueryResult<>(null, affected);
        } catch (SQLException e) {
            throw DBException.fromSQLException(e, "update(" + id + ")");
        } finally {
            poolManager.releaseConnection(c);
        }
    }

    // Crea una transacción desacoplada que maneja su propia conexión dedicada
    public DBTransaction transaction() throws DBException {
        return new SimpleTransaction(acquire(), poolManager);
    }

    /**
     * Crea un batch desacoplado que solo acepta IDs predefinidos.
     */
    public DBQueryBatch batch() {
        return new SimpleBatch(this);
    }

    /**
     * Crea un ejecutor para queries desde archivo .sql.
     */
    public DBQueryFile queryFiles() {
        return new SimpleQueryFile(this);
    }

    /**
     * Atajo: agrega IDs y ejecuta un batch en una sola llamada.
     */
    public DBQueryResult<int[]> executeBatch(List<DBQueryId> ids) throws DBException {
        DBQueryBatch batch = batch();
        for (DBQueryId id : ids) {
            batch.addQuery(id);
        }
        return batch.executeBatch();
    }

    /**
     * Atajo: ejecuta archivo .sql usando el ejecutor de archivos.
     */
    public DBQueryResult<?> queryFromFile(Path sqlFile) throws DBException {
        return queryFiles().queryFromFile(sqlFile);
    }

    /**
     * Atajo: carga sentencias SQL de un archivo.
     */
    public List<String> loadQueriesFromFile(Path sqlFile) throws DBException {
        return queryFiles().loadQueriesFromFile(sqlFile);
    }

    /**
     * Transacción simple que trabaja con una conexión dedicada.
     */
    // Implementa DBTransaction y maneja su propia conexión
    private static final class SimpleTransaction implements DBTransaction {
        private final Connection c;
        private final PoolManager poolManager;
        private boolean active = false;

        // Constructor privado, se crea desde DBComponent.transaction
        private SimpleTransaction(Connection c, PoolManager poolManager) {
            this.c = c;
            this.poolManager = poolManager;
        }

        @Override
        // Inicia la transacción desactivando el auto-commit de la conexión
        public void begin() throws DBException {
            try {
                c.setAutoCommit(false);
                active = true;
            } catch (SQLException e) {
                throw DBException.fromSQLException(e, "tx.begin");
            }
        }

        @Override
        // Confirma la transacción y libera la conexión
        public void commit() throws DBException {
            try {
                c.commit();
            } catch (SQLException e) {
                throw DBException.fromSQLException(e, "tx.commit");
            } finally {
                end();
            }
        }

        @Override
        // Revierte " "
        public void rollback() throws DBException {
            try {
                c.rollback();
            } catch (SQLException e) {
                throw DBException.fromSQLException(e, "tx.rollback");
            } finally {
                end();
            }
        }

        // Finaliza la transacción, restablece el auto-commit y libera la conexión
        private void end() {
            try {
                c.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
            active = false;
            poolManager.releaseConnection(c);
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        // Si la transacción sigue activa al cerrar,
        // se revierte para evitar dejar la conexión en un estado inconsistente, luego libera la conexión
        public void close() throws DBException {
            if (active) {
                rollback();
                return;
            }
            poolManager.releaseConnection(c);
        }
    }

    // Batch simple que solo acepta IDs predefinidos y ejecuta usando Statement.addBatch/executeBatch.
    private static final class SimpleBatch implements DBQueryBatch {
        private final DBComponent owner;
        private final List<DBQueryId> pending = new ArrayList<>();

        private SimpleBatch(DBComponent owner) {
            this.owner = owner;
        }

        @Override
        public synchronized void clearBatch() {
            pending.clear();
        }

        @Override
        public synchronized void addQuery(DBQueryId id) throws DBException {
            if (id == null) {
                throw new DBException(DBException.Category.CONFIG, null, "DBQueryId no puede ser null");
            }
            pending.add(id);
        }

        @Override
        // Ejecuta el batch de IDs acumulados, sumando total de filas afectadas, luego limpia el batch
        public synchronized DBQueryResult<int[]> executeBatch() throws DBException {
            Connection c = owner.acquire();
            try (Statement st = c.createStatement()) {
                st.setQueryTimeout(owner.statementTimeoutSeconds());
                for (DBQueryId id : pending) {
                    st.addBatch(owner.queries.sql(id));
                }
                int[] result = st.executeBatch();
                int affected = Arrays.stream(result).sum();
                pending.clear();
                return new DBQueryResult<>(result, affected);
            } catch (SQLException e) {
                throw DBException.fromSQLException(e, "batch.executeBatch");
            } finally {
                owner.poolManager.releaseConnection(c);
            }
        }
    }

    //  Ejecutor simple de archivos .sql que carga sentencias
    private static final class SimpleQueryFile implements DBQueryFile {
        private final DBComponent owner;

        private SimpleQueryFile(DBComponent owner) {
            this.owner = owner;
        }

        @Override
        // Ejecuta sentencias SQL contenidas en el archivo, contando el total de sentencias, filas afectadas y filas resultado.
        public DBQueryResult<?> queryFromFile(Path sqlFile) throws DBException {
            List<String> statements = loadQueriesFromFile(sqlFile);
            if (statements.isEmpty()) {
                return new DBQueryResult<>(new QueryFileSummary(0, 0, 0), 0);
            }

            Connection c = owner.acquire();
            int total = 0;
            int updates = 0;
            int resultRows = 0;

            //  Ejecuta cada sentencia, sumando el total de sentencias, filas afectadas por actualizaciones y
            //  filas resultado de consultas, luego devuelve un resumen
            try (Statement st = c.createStatement()) {
                st.setQueryTimeout(owner.statementTimeoutSeconds());
                for (String sql : statements) {
                    total++;
                    boolean hasResultSet = st.execute(sql);
                    if (hasResultSet) {
                        try (ResultSet rs = st.getResultSet()) {
                            while (rs != null && rs.next()) {
                                resultRows++;
                            }
                        }
                    } else {
                        int affected = st.getUpdateCount();
                        if (affected > 0) {
                            updates += affected;
                        }
                    }
                }
                return new DBQueryResult<>(new QueryFileSummary(total, updates, resultRows), updates);
            } catch (SQLException e) {
                throw DBException.fromSQLException(e, "queryFromFile(" + sqlFile + ")");
            } finally {
                owner.poolManager.releaseConnection(c);
            }
        }

        // Carga sentencias SQL de un archivo, ignorando líneas vacías
        @Override
        // Lee el contenido del archivo, lo divide en sentencias SQL usando ';' como separador
        public List<String> loadQueriesFromFile(Path sqlFile) throws DBException {
            if (sqlFile == null) {
                throw new DBException(DBException.Category.CONFIG, null, "sqlFile no puede ser null");
            }
            try {
                String content = Files.readString(sqlFile, StandardCharsets.UTF_8);
                return splitSqlStatements(content);
            } catch (Exception e) {
                throw new DBException(DBException.Category.IO, null,
                        "Error leyendo archivo SQL: " + sqlFile, e);
            }
        }

        // Divide el contenido del archivo en sentencias SQL usando ';' como separador
        private List<String> splitSqlStatements(String content) {
            List<String> statements = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (String rawLine : content.split("\\R")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }

                current.append(rawLine).append('\n');
                if (line.endsWith(";")) {
                    String sql = current.toString().trim();
                    if (sql.endsWith(";")) {
                        sql = sql.substring(0, sql.length() - 1).trim();
                    }
                    if (!sql.isEmpty()) {
                        statements.add(sql);
                    }
                    current.setLength(0);
                }
            }

            String tail = current.toString().trim();
            if (!tail.isEmpty()) {
                statements.add(tail);
            }

            return statements;
        }
    }

    // Resumen de ejecución de un archivo SQL
    public record QueryFileSummary(int statements, int affectedRows, int resultRows) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "statements=%d, affectedRows=%d, resultRows=%d",
                    statements, affectedRows, resultRows);
        }
    }
}