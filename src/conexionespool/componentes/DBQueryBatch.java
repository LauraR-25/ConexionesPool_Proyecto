package conexionespool.componentes;

public interface DBQueryBatch {

    void clearBatch() throws DBException;

    DBQueryResult<int[]> executeBatch() throws DBException;

    void addQuery(DBQueryId id) throws DBException;
}