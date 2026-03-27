package conexionespool.componentes;

// Interfaz para ejecutar un batch de consultas SQL
public interface DBQueryBatch {

    void clearBatch() throws DBException;

    DBQueryResult<int[]> executeBatch() throws DBException;

    void addQuery(DBQueryId id) throws DBException;
}