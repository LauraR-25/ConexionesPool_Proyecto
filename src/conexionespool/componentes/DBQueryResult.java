package conexionespool.componentes;

// Clase que encapsula el resultado de una consulta a la base de datos, incluye el resultado mapeado y número de filas afectadas
public class DBQueryResult<T> {
    private final T result;
    private final int affectedRows;

    public DBQueryResult(T result, int affectedRows) {
        this.result = result;
        this.affectedRows = affectedRows;
    }

    public T getResult() {
        return result;
    }

    public int getAffectedRows() {
        return affectedRows;
    }
}