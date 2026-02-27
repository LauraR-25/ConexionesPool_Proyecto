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

        // Crear componentes
        txtPeticiones = new TextField(String.valueOf(config.obtenerEntero("PETICIONES_POR_DEFECTO")));
        btnSimular = new Button("🚀 Iniciar simulación");
        btnFreno = new Button("🛑 Freno de emergencia");
        barraSinPool = new ProgressBar(0);
        barraConPool = new ProgressBar(0);
        lblEstadoSin = new Label("⏳ Sin pool: esperando...");
        lblEstadoCon = new Label("⏳ Con pool: esperando...");
        lblResumen = new Label();
        grafica = new GraficoBarras();
        grafica.setVisible(false);

        // Aplicar estilos CSS
        String css = """
            .root {
                -fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);
                -fx-font-family: 'Segoe UI', 'Arial', sans-serif;
                -fx-padding: 30;
            }
            .label {
                -fx-text-fill: #e0e0e0;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
            }
            .title-label {
                -fx-font-size: 28px;
                -fx-text-fill: #a88ff0;
                -fx-font-weight: bold;
                -fx-effect: dropshadow(gaussian, #a88ff0, 10, 0.3, 0, 2);
            }
            .text-field {
                -fx-background-color: #0f0f1f;
                -fx-text-fill: #ffffff;
                -fx-border-color: #a88ff0;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
                -fx-font-size: 14px;
                -fx-padding: 8 12;
            }
            .button {
                -fx-background-color: linear-gradient(to bottom, #a88ff0, #7c3aed);
                -fx-text-fill: #ffffff;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 8;
                -fx-border-radius: 8;
                -fx-padding: 10 20;
                -fx-cursor: hand;
                -fx-effect: dropshadow(gaussian, #a88ff0, 8, 0.2, 0, 2);
            }
            .button:hover {
                -fx-background-color: linear-gradient(to bottom, #b69eff, #a88ff0);
            }
            .button:frenado {
                -fx-background-color: linear-gradient(to bottom, #ff4b4b, #b91c1c);
            }
            .progress-bar {
                -fx-accent: #a88ff0;
                -fx-background-radius: 10;
                -fx-border-radius: 10;
                -fx-pref-height: 20;
            }
            .progress-bar .track {
                -fx-background-color: #0f0f1f;
            }
            .status-label {
                -fx-text-fill: #b0b0b0;
                -fx-font-size: 13px;
                -fx-font-style: italic;
            }
            .result-label {
                -fx-text-fill: #a88ff0;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-padding: 10 0 0 0;
            }
            .graph-box {
                -fx-background-color: #1e1e2f;
                -fx-border-radius: 15;
                -fx-background-radius: 15;
                -fx-padding: 15;
                -fx-effect: dropshadow(gaussian, #a88ff0, 8, 0.1, 0, 2);
            }
            """;

        Scene scene = new Scene(crearLayout(), 900, 750);
        scene.getStylesheets().add("data:text/css," + css.replace("\n", "").replace(" ", " "));
        stage.setScene(scene);
        stage.setTitle("ConexionesPool - Simulación");
        stage.show();

        btnSimular.setOnAction(e -> iniciarSimulacion());
        btnFreno.setOnAction(e -> {
            if (freno != null) freno.activar();
            btnFreno.setStyle("-fx-background-color: linear-gradient(to bottom, #ff4b4b, #b91c1c);");
        });
    }

    private VBox crearLayout() {
        Label titulo = new Label("Pool de Conexiones - Simulador");
        titulo.getStyleClass().add("title-label");

        HBox peticionesBox = new HBox(10, new Label("Número de peticiones:"), txtPeticiones);
        peticionesBox.setAlignment(Pos.CENTER);

        HBox botonesBox = new HBox(20, btnSimular, btnFreno);
        botonesBox.setAlignment(Pos.CENTER);

        VBox sinPoolBox = new VBox(5,
                new Label("📊 Sin pool de conexiones"),
                barraSinPool,
                lblEstadoSin
        );
        sinPoolBox.getStyleClass().add("graph-box");

        VBox conPoolBox = new VBox(5,
                new Label("⚡ Con pool de conexiones"),
                barraConPool,
                lblEstadoCon
        );
        conPoolBox.getStyleClass().add("graph-box");

        lblResumen.getStyleClass().add("result-label");

        VBox graficaBox = new VBox(grafica);
        graficaBox.getStyleClass().add("graph-box");
        graficaBox.setVisible(false);
        graficaBox.managedProperty().bind(graficaBox.visibleProperty());

        VBox root = new VBox(20,
                titulo,
                peticionesBox,
                botonesBox,
                sinPoolBox,
                conPoolBox,
                lblResumen,
                graficaBox
        );
        root.setAlignment(Pos.TOP_CENTER);
        root.getStyleClass().add("root");
        return root;
    }

    private void iniciarSimulacion() {
        btnSimular.setDisable(true);
        btnFreno.setDisable(false);
        grafica.setVisible(false);
        lblResumen.setText("");
        barraSinPool.setProgress(0);
        barraConPool.setProgress(0);
        lblEstadoSin.setText("⏳ Sin pool: en progreso...");
        lblEstadoCon.setText("⏳ Con pool: en progreso...");

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
                        String.format("✅ Sin pool: %d exitosas, %d fallidas (%.2f%%)",
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
                            String.format("⚡ Con pool: %d exitosas, %d fallidas (%.2f%%)",
                                    exitosasCon, fallidasCon, pctCon));
                    grafica.mostrarResultados(exitosasSin, fallidasSin, exitosasCon, fallidasCon);
                    grafica.setVisible(true);

                    String mejor = (pctSin > pctCon) ? "SIN POOL" :
                            (pctCon > pctSin) ? "CON POOL" : "EMPATE";
                    lblResumen.setText("🏆 Mejor rendimiento: " + mejor);
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
                    btnFreno.setStyle(""); // restaurar estilo
                });
            }
        }).start();
    }
}