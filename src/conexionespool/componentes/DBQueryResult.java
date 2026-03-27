package conexionespool.componentes;

// Clase que encapsula el resultado de una consulta a la base de datos,
// incluye el resultado mapeado y número de filas afectadas
public class DBQueryResult<T> {
    private final T result;
    private final int affectedRows;

    public DBQueryResult(T result, int affectedRows) {
        this.result = result;
        this.affectedRows = affectedRows;
    }

    // Accede al resultado y número de filas afectadas
    public T getResult() {
        return result;
    }

    // Devuelve el número de filas afectadas por la consulta
    public int getAffectedRows() {
        return affectedRows;
    }
}