package conexionespool.componentes;

import java.sql.SQLException;

// Excepción personalizada para errores relacionados con la base de datos
public class DBException extends Exception {

    public enum Category {
        CONNECTION,
        AUTH,
        TIMEOUT,
        SYNTAX,
        CONSTRAINT,
        TRANSACTION,
        IO,
        CONFIG,
        UNKNOWN
    }

    // Campos adicionales para categorizar y contextualizar el error
    private final Category category;
    private final String errorCode;
    private final String sqlState;
    private final Integer vendorCode;
    private final String context;


    // Constructores de conveniencia para diferentes niveles de detalle
    public DBException(String message) {
        this(Category.UNKNOWN, null, message, null, null, null, null);
    }

    //  Envuelve excepciones genéricas sin perder la categoría ni el mensaje original
    public DBException(String message, Throwable cause) {
        this(Category.UNKNOWN, null, message, cause, null, null, null);
    }

    // Crea excepciones con una categoría específica y un mensaje, sin necesidad de un cause o detalles adicionales
    public DBException(Category category, String errorCode, String message) {
        this(category, errorCode, message, null, null, null, null);
    }

    // Permite incluir un cause para mantener la cadena de excepciones
    public DBException(Category category, String errorCode, String message, Throwable cause) {
        this(category, errorCode, message, cause, null, null, null);
    }

    // Constructor completo que permite especificar todos los detalles
    public DBException(Category category,
                       String errorCode,
                       String message,
                       Throwable cause,
                       String sqlState,
                       Integer vendorCode,
                       String context) {
        super(message, cause);
        this.category = category == null ? Category.UNKNOWN : category;
        this.errorCode = errorCode;
        this.sqlState = sqlState;
        this.vendorCode = vendorCode;
        this.context = context;
    }

    // Getters para acceder a los detalles del error
    // La categoría del error
    public Category getCategory() {
        return category;
    }

    // Código de error específico del proveedor de base de datos
    public String getErrorCode() {
        return errorCode;
    }

    // Código SQLState estándar que clasifica el error según la especificación SQL
    public String getSqlState() {
        return sqlState;
    }

    // Código de error específico del proveedor de base de datos, puede proporcionar información adicional sobre el error
    public Integer getVendorCode() {
        return vendorCode;
    }

    // Contexto adicional sobre dónde o cómo ocurrió el error
    public String getContext() {
        return context;
    }

    // Método estático para convertir una SQLException en una DBException
    public static DBException fromSQLException(SQLException e, String contextMessage) {
        if (e == null) return new DBException(Category.UNKNOWN, null, contextMessage);

        String sqlState = e.getSQLState();
        Integer vendor = e.getErrorCode();

        Category cat = Category.UNKNOWN;
        if (sqlState != null && sqlState.length() >= 2) {
            String cls = sqlState.substring(0, 2);
            switch (cls) {
                case "08" -> cat = Category.CONNECTION;
                case "28" -> cat = Category.AUTH;
                case "40" -> cat = Category.TRANSACTION;
                case "42" -> cat = Category.SYNTAX;
                case "23" -> cat = Category.CONSTRAINT;
                default -> cat = Category.UNKNOWN;
            }
        }

        String msg = contextMessage == null || contextMessage.isBlank()
                ? e.getMessage()
                : (contextMessage + ": " + e.getMessage());

        return new DBException(cat, sqlState, msg, e, sqlState, vendor, contextMessage);
    }
}