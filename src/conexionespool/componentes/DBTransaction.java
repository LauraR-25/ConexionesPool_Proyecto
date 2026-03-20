package conexionespool.componentes;

public interface DBTransaction extends AutoCloseable {
    void begin() throws DBException;

    void commit() throws DBException;

    void rollback() throws DBException;

    default boolean isActive() {
        return false;
    }

    @Override
    void close() throws DBException;
}