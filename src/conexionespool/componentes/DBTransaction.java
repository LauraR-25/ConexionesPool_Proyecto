package conexionespool.componentes;

// Interfaz que representa una transacción de base de datos
public interface DBTransaction extends AutoCloseable {
    // Inicia
    void begin() throws DBException;

    // Confirma la transacción
    void commit() throws DBException;

    //  Revierte la transacción
    void rollback() throws DBException;

    //  Indica si la transacción está activa (iniciada pero no confirmada ni revertida)
    default boolean isActive() {
        return false;
    }

    @Override
    // Cierra transacción
    void close() throws DBException;
}