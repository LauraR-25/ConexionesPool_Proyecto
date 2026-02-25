package conexionesPool;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfiguracionEntorno {
    private final Properties propiedades = new Properties();

    public ConfiguracionEntorno(String rutaArchivo) {
        try (FileInputStream fis = new FileInputStream(rutaArchivo)) {
            propiedades.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Error al cargar archivo de configuración: " + rutaArchivo, e);
        }
    }

    public String obtener(String clave) {
        return propiedades.getProperty(clave);
    }

    public int obtenerEntero(String clave) {
        return Integer.parseInt(propiedades.getProperty(clave));
    }

    public long obtenerLargo(String clave) {
        return Long.parseLong(propiedades.getProperty(clave));
    }
}