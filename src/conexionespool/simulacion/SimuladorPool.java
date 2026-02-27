package conexionespool.simulacion;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.pool.AdministradorPool;
import conexionespool.util.Freno;
import conexionespool.util.LoggerMuestras;

import java.sql.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimuladorPool extends Simulador {
    private final AdministradorPool admin;

    public SimuladorPool(int totalMuestras, int reintentosMaximos, Supplier<String> proveedorQuery,
                         Freno freno, LoggerMuestras logger,
                         AdministradorPool admin) {
        super(totalMuestras, reintentosMaximos, proveedorQuery, freno, logger);
        this.admin = admin;
    }

    @Override
    protected void ejecutarMuestra(int id, String query, ContadorEstadisticas contador, Consumer<Double> actualizador) {
        boolean exito = false;
        String mensajeError = "";
        int reintentos = 0;
        Connection conn = null;

        while (reintentos <= reintentosMaximos && !exito && !freno.estaActivado()) {
            if (reintentos > 0) {
                try { Thread.sleep(random.nextInt(100)); } catch (InterruptedException e) { break; }
            }

            try {
                conn = admin.tomarConexion();
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery(query);
                    if (rs.next()) exito = true;
                    rs.close();
                } finally {
                    admin.devolverConexion(conn);
                }
                break;
            } catch (SQLException e) {
                mensajeError = e.getMessage();
                reintentos++;
                logger.log(String.format("Pool %d reintento %d: %s", id, reintentos, mensajeError));
                if (conn != null) admin.devolverConexion(conn);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Resultado resultado = new Resultado(id, exito, exito ? "OK" : mensajeError, reintentos, System.currentTimeMillis());
        contador.registrar(resultado);
        logger.log(resultado.formatoLog());
        actualizador.accept((double) id / totalMuestras);
    }
}