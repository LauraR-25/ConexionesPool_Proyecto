package conexionespool.componentes;

import java.sql.ResultSet;
import java.sql.SQLException;

// Mapear una fila de un ResultSet a un tipo T
@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(ResultSet rs) throws SQLException;
}