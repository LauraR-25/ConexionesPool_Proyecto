package conexionespool.ui;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.pool.AdministradorPool;
import conexionespool.pool.PoolConexiones;
import conexionespool.simulacion.SimuladorPool;
import conexionespool.simulacion.SimuladorRaw;
import conexionespool.util.ConfiguracionEntorno;
import conexionespool.util.Freno;
import conexionespool.util.LoggerMuestras;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Random;
import java.util.function.Supplier;

public class VentanaPrincipal extends Application {

    private TextField txtPeticiones;
    private ProgressBar barraSinPool, barraConPool;
    private Label lblEstadoSin, lblEstadoCon, lblResumen;
    private GraficoBarras grafica;
    private Button btnSimular, btnFreno;
    private ConfiguracionEntorno config;
    private Freno freno;

    @Override
    public void start(Stage stage) {
        config = new ConfiguracionEntorno(".env");

        txtPeticiones = new TextField(String.valueOf(config.obtenerEntero("PETICIONES_POR_DEFECTO")));
        btnSimular = new Button("Iniciar simulación");
        btnFreno = new Button("Freno de emergencia");
        barraSinPool = new ProgressBar(0);
        barraConPool = new ProgressBar(0);
        lblEstadoSin = new Label("Sin pool: esperando...");
        lblEstadoCon = new Label("Con pool: esperando...");
        lblResumen = new Label();
        grafica = new GraficoBarras();
        grafica.setVisible(false);

        VBox root = new VBox(15,
                new Label("Simulador de Pool de Conexiones"),
                new HBox(10, new Label("Número de peticiones:"), txtPeticiones),
                new HBox(10, btnSimular, btnFreno),
                new VBox(5, new Label("Sin pool:"), barraSinPool, lblEstadoSin),
                new VBox(5, new Label("Con pool:"), barraConPool, lblEstadoCon),
                lblResumen,
                grafica
        );
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 25; -fx-background-color: #1e1e2f; -fx-text-fill: white;");
        Scene scene = new Scene(root, 800, 700);
        stage.setScene(scene);
        stage.setTitle("ConexionesPool - Simulación");
        stage.show();

        btnSimular.setOnAction(e -> iniciarSimulacion());
        btnFreno.setOnAction(e -> {
            if (freno != null) freno.activar();
        });
    }

    private void iniciarSimulacion() {
        btnSimular.setDisable(true);
        btnFreno.setDisable(false);
        grafica.setVisible(false);
        lblResumen.setText("");
        barraSinPool.setProgress(0);
        barraConPool.setProgress(0);
        lblEstadoSin.setText("Sin pool: en progreso...");
        lblEstadoCon.setText("Con pool: en progreso...");

        int numPeticiones;
        try {
            numPeticiones = Integer.parseInt(txtPeticiones.getText());
        } catch (NumberFormatException ex) {
            lblResumen.setText("Número inválido");
            btnSimular.setDisable(false);
            return;
        }

        String url = "jdbc:postgresql://" + config.obtener("DB_HOST") + ":"
                + config.obtener("DB_PORT") + "/" + config.obtener("DB_NAME");
        String user = config.obtener("DB_USER");
        String pass = config.obtener("DB_PASSWORD");
        int tamPool = config.obtenerEntero("POOL_SIZE");
        long timeout = config.obtenerLargo("POOL_TIMEOUT");
        int reintentosMax = config.obtenerEntero("REINTENTOS_MAXIMOS");
        String[] queriesArray = config.obtenerQueries();
        Supplier<String> proveedorQueries = () -> queriesArray[new Random().nextInt(queriesArray.length)];

        freno = new Freno();
        LoggerMuestras logger = new LoggerMuestras();

        PoolConexiones pool = new PoolConexiones(url, user, pass, tamPool, timeout);
        AdministradorPool admin = new AdministradorPool(pool);

        new Thread(() -> {
            try {
                // --- Sin pool ---
                ContadorEstadisticas contadorSin = new ContadorEstadisticas();
                Thread hiloContadorSin = new Thread(contadorSin);
                hiloContadorSin.start();

                SimuladorRaw simuladorRaw = new SimuladorRaw(
                        numPeticiones, reintentosMax, proveedorQueries,
                        freno, logger, url, user, pass
                );

                simuladorRaw.ejecutar(contadorSin, progreso ->
                        Platform.runLater(() -> barraSinPool.setProgress(progreso)));

                contadorSin.detener();
                hiloContadorSin.join();

                int exitosasSin = contadorSin.getExitosas();
                int fallidasSin = contadorSin.getFallidas();
                double pctSin = contadorSin.getPorcentajeExito();

                Platform.runLater(() -> lblEstadoSin.setText(
                        String.format("Sin pool: %d exitosas, %d fallidas (%.2f%%)",
                                exitosasSin, fallidasSin, pctSin)));

                // --- Con pool ---
                ContadorEstadisticas contadorCon = new ContadorEstadisticas();
                Thread hiloContadorCon = new Thread(contadorCon);
                hiloContadorCon.start();

                SimuladorPool simuladorPool = new SimuladorPool(
                        numPeticiones, reintentosMax, proveedorQueries,
                        freno, logger, admin
                );

                simuladorPool.ejecutar(contadorCon, progreso ->
                        Platform.runLater(() -> barraConPool.setProgress(progreso)));

                contadorCon.detener();
                hiloContadorCon.join();

                int exitosasCon = contadorCon.getExitosas();
                int fallidasCon = contadorCon.getFallidas();
                double pctCon = contadorCon.getPorcentajeExito();

                Platform.runLater(() -> {
                    lblEstadoCon.setText(
                            String.format("Con pool: %d exitosas, %d fallidas (%.2f%%)",
                                    exitosasCon, fallidasCon, pctCon));

                    // 🔧 CAMBIA "mostrarResultados" por el nombre real del método en GraficoBarras
                    grafica.mostrarResultados(exitosasSin, fallidasSin, exitosasCon, fallidasCon);
                    grafica.setVisible(true);

                    String mejor = (pctSin > pctCon) ? "SIN POOL" :
                            (pctCon > pctSin) ? "CON POOL" : "EMPATE";
                    lblResumen.setText("Mejor rendimiento: " + mejor);
                });

            } catch (Exception e) {
                Platform.runLater(() -> lblResumen.setText("Error: " + e.getMessage()));
                e.printStackTrace();
            } finally {
                try {
                    admin.cerrarPool();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                Platform.runLater(() -> {
                    btnSimular.setDisable(false);
                    btnFreno.setDisable(true);
                });
            }
        }).start();
    }
}