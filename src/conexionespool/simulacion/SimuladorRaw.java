package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.util.Freno;
import conexionespool.util.LoggerMuestras;

import java.sql.*;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimuladorRaw extends Simulador {
    private final String url;
    private final String usuario;
    private final String password;

    public SimuladorRaw(int totalMuestras, int reintentosMaximos, Supplier<String> proveedorQuery,
                        Freno freno, LoggerMuestras logger,
                        String url, String usuario, String password) {
        super(totalMuestras, reintentosMaximos, proveedorQuery, freno, logger);
        this.url = url;
        this.usuario = usuario;
        this.password = password;
    }

    @Override
    protected void ejecutarMuestra(int id, String query, ContadorEstadisticas contador, Consumer<Double> actualizador) {
        boolean exito = false;
        String mensajeError = "";
        int reintentos = 0;

        while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
            if (reintentos > 0) {
                try {
                    Thread.sleep(random.nextInt(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try (Connection conn = DriverManager.getConnection(url, usuario, password)) {
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery(query);
                    if (rs.next()) exito = true;
                    rs.close();
                }
                break;
            } catch (SQLException e) {
                mensajeError = e.getMessage();
                reintentos++;
                logger.log(String.format("Raw %d reintento %d: %s", id, reintentos, mensajeError));
            }
        }

        Resultado resultado = new Resultado(id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis());
        contador.registrar(resultado);
        logger.log(resultado.formatoLog());
        actualizador.accept((double) id / totalMuestras);
    }
}