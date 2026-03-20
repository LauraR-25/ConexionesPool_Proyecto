package conexionespool.componentes;

import java.nio.file.Path;
import java.util.List;

public interface DBQueryFile {
    DBQueryResult<?> queryFromFile(Path sqlFile) throws DBException;

    List<String> loadQueriesFromFile(Path sqlFile) throws DBException;
}