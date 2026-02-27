package conexionespool.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Configuracion {
    private final Properties props = new Properties();

    public Configuracion(String ruta) {
        try (FileInputStream fis = new FileInputStream(ruta)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar configuración: " + ruta, e);
        }
    }

    public String getHost() { return props.getProperty("db.host"); }
    public int getPuerto() { return Integer.parseInt(props.getProperty("db.port")); }
    public String getBase() { return props.getProperty("db.name"); }
    public String getUsuario() { return props.getProperty("db.user"); }
    public String getPassword() { return props.getProperty("db.password"); }

    public String getUrl() {
        return "jdbc:postgresql://" + getHost() + ":" + getPuerto() + "/" + getBase();
    }

    public String getQueryPrincipal() { return props.getProperty("query.principal"); }

    public List<String> getQueriesSecundarias() {
        List<String> lista = new ArrayList<>();
        for (int i = 1; ; i++) {
            String q = props.getProperty("query.sec" + i);
            if (q == null) break;
            lista.add(q);
        }
        return lista;
    }

    public int getMuestrasIniciales() { return Integer.parseInt(props.getProperty("muestras.inicial")); }
    public int getMuestrasFinal() { return Integer.parseInt(props.getProperty("muestras.final")); }
    public int getPaso() { return Integer.parseInt(props.getProperty("muestras.paso")); }
    public int getReintentosPorMuestra() { return Integer.parseInt(props.getProperty("reintentos")); }
    public int getTamanoPool() { return Integer.parseInt(props.getProperty("pool.size")); }
    public long getPoolTimeout() { return Long.parseLong(props.getProperty("pool.timeout")); }
}