package conexionespool.componentes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repositorio de queries predefinidas.
 */
public final class DBQueries {
    private final Map<String, String> queries;

    private DBQueries(Map<String, String> queries) {
        this.queries = Collections.unmodifiableMap(new HashMap<>(queries));
    }

    public static DBQueries load(String location) throws DBException {
        if (location == null || location.isBlank()) {
            throw new DBException("location no puede ser null/vacío");
        }

        if (location.startsWith("classpath:")) {
            String cp = location.substring("classpath:".length());
            return loadFromClasspath(cp);
        }

        if (location.startsWith("file:")) {
            String fp = location.substring("file:".length());
            return loadFromFile(Path.of(fp));
        }

        if (location.endsWith(".properties") || location.endsWith(".json") ||
                location.endsWith(".yaml") || location.endsWith(".yml") || location.endsWith(".toml")) {
            if (location.startsWith("/") || location.startsWith("db/")) {
                return loadFromClasspath(location);
            }
            Path path = Path.of(location);
            if (Files.exists(path)) {
                return loadFromFile(path);
            }
            return loadFromClasspath(location);
        }

        throw new DBException(DBException.Category.CONFIG, null,
                "Ubicación de queries no soportada: " + location +
                        " (usa classpath:/..., file:/... o ruta de archivo)");
    }

    // Carga desde classpath, detectando formato por extensión
    // Si no se encuentra el recurso, lanza DBException con mensaje claro
    public static DBQueries loadFromClasspath(String resourcePath) throws DBException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new DBException("resourcePath no puede ser null/vacío");
        }
        String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;

        try (InputStream in = DBQueries.class.getResourceAsStream(normalized)) {
            if (in == null) {
                throw new DBException(DBException.Category.CONFIG, null,
                        "No se encontró el recurso de queries: " + normalized);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> map = parseByExtension(normalized, text);
            return new DBQueries(map);
        } catch (IOException e) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "Error cargando queries desde recurso: " + normalized, e);
        }
    }

    // Carga desde sistema de archivos, detectando formato por extensión
    public static DBQueries loadFromFile(Path filePath) throws DBException {
        if (filePath == null) {
            throw new DBException("filePath no puede ser null");
        }

        try {
            String text = Files.readString(filePath, StandardCharsets.UTF_8);
            Map<String, String> map = parseByExtension(filePath.toString(), text);
            return new DBQueries(map);
        } catch (IOException e) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "Error cargando queries desde archivo: " + filePath, e);
        }
    }

    // Obtiene la query SQL asociada al ID dado
    public String sql(DBQueryId id) throws DBException {
        Objects.requireNonNull(id, "id");
        String sql = queries.get(id.value());
        if (sql == null) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "No existe query predefinida para id='" + id.value() + "'");
        }
        return sql;
    }

    public Set<String> ids() {
        return queries.keySet();
    }

    // Detecta el formato del archivo por su extensión y delega al parser correspondiente
    // Si la extensión no es reconocida, lanza DBException con mensaje claro
    private static Map<String, String> parseByExtension(String normalizedResourcePath, String text) throws DBException {
        String path = normalizedResourcePath.toLowerCase();
        if (path.endsWith(".properties")) {
            return parseProperties(text);
        }
        if (path.endsWith(".json")) {
            return parseJson(text);
        }
        if (path.endsWith(".yaml") || path.endsWith(".yml")) {
            return parseYaml(text);
        }
        if (path.endsWith(".toml")) {
            return parseToml(text);
        }

        throw new DBException(DBException.Category.CONFIG, null,
                "Formato de archivo no soportado para queries: " + normalizedResourcePath +
                        " (usa .properties, .json, .yaml/.yml o .toml)");
    }

    // Parsea archivos .properties usando java.util.Properties
    private static Map<String, String> parseProperties(String text) throws DBException {
        try {
            Properties p = new Properties();
            p.load(new java.io.StringReader(text));

            Map<String, String> map = new HashMap<>();
            for (String name : p.stringPropertyNames()) {
                String sql = p.getProperty(name);
                if (sql != null) {
                    map.put(name.trim(), sql.trim());
                }
            }
            return map;
        } catch (IOException e) {
            throw new DBException(DBException.Category.CONFIG, null, "Error parseando archivo .properties", e);
        }
    }

    // Parsea archivos JSON con formato plano { "id": "sql", ... } usando regex simple
    private static Map<String, String> parseJson(String text) throws DBException {
        String src = text.trim();
        if (!src.startsWith("{") || !src.endsWith("}")) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "JSON inválido: se esperaba un objeto { ... }");
        }

        Map<String, String> map = new LinkedHashMap<>();
        Pattern pairPattern = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher m = pairPattern.matcher(src);
        while (m.find()) {
            String key = m.group(1).trim();
            String value = unescapeJson(m.group(2)).trim();
            map.put(key, value);
        }

        if (map.isEmpty()) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "JSON de queries vacío o con formato no soportado. Usa pares string:string.");
        }
        return map;
    }

    // Deshace las secuencias de escape comunes en JSON
    private static String unescapeJson(String value) {
        return value
                .replace("\\\\n", "\n")
                .replace("\\\\r", "\r")
                .replace("\\\\t", "\t")
                .replace("\\\\\"", "\"")
                .replace("\\\\\\\\", "\\");
    }

    // Parsea archivos YAML con formato plano key: value, ignorando comentarios y líneas vacías
    private static Map<String, String> parseYaml(String text) throws DBException {
        Map<String, String> map = new LinkedHashMap<>();
        String[] lines = text.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            value = stripWrappingQuotes(value);
            if (!key.isEmpty() && !value.isEmpty()) {
                map.put(key, value);
            }
        }

        if (map.isEmpty()) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "YAML de queries vacío o no compatible. Usa formato plano key: value");
        }
        return map;
    }

    // "" TOML con formato plano key = "value", ignorando comentarios, secciones, líneas vacías
    private static Map<String, String> parseToml(String text) throws DBException {
        Map<String, String> map = new LinkedHashMap<>();
        String[] lines = text.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                continue;
            }
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            value = stripWrappingQuotes(value);
            if (!key.isEmpty() && !value.isEmpty()) {
                map.put(key, value);
            }
        }

        if (map.isEmpty()) {
            throw new DBException(DBException.Category.CONFIG, null,
                    "TOML de queries vacío o no compatible. Usa formato plano key = \"value\"");
        }
        return map;
    }

    // Elimina comillas simples o dobles que envuelven el valor, si existen, y recorta espacios
    private static String stripWrappingQuotes(String value) {
        Objects.requireNonNull(value, "value");
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value.trim();
    }
}