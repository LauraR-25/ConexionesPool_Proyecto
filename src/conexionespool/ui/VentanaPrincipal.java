package conexionespool.ui;

import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.pool.AdministradorPool;
import conexionespool.pool.PoolConexiones;
import conexionespool.simulacion.SimuladorPool;
import conexionespool.simulacion.SimuladorRaw;
import conexionespool.util.ConfiguracionEntorno;
import conexionespool.util.Freno;
import conexionespool.util.LoggerMuestras;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Random;
import java.util.function.Supplier;

public class VentanaPrincipal extends Application {

    private TextField txtPeticiones;
    private ProgressBar barraSinPool, barraConPool;
    private Label lblEstadoSin, lblEstadoCon, lblResumen;
    private GraficoBarras graficaFinal;
    private GraficoTorta graficoTortaSin, graficoTortaCon;
    private TextArea areaLogs;
    private Button btnSimularRaw, btnSimularPool, btnSimularAmbos, btnFreno;
    private ConfiguracionEntorno config;
    private Freno freno;

    private ContadorEstadisticas contadorSin, contadorCon;
    private Timeline timelineSin, timelineCon;

    @Override
    public void start(Stage stage) {
        config = new ConfiguracionEntorno(".env");

        txtPeticiones = new TextField("50");
        btnSimularRaw = new Button("🚀 Solo Raw");
        btnSimularPool = new Button("⚡ Solo Pool");
        btnSimularAmbos = new Button("🔁 Ambas simulaciones");
        btnFreno = new Button("🛑 Freno de emergencia");

        barraSinPool = new ProgressBar(0);
        barraConPool = new ProgressBar(0);
        lblEstadoSin = new Label("⏳ Sin pool: esperando...");
        lblEstadoCon = new Label("⏳ Con pool: esperando...");
        lblResumen = new Label();
        graficaFinal = new GraficoBarras();
        graficaFinal.setVisible(false);
        graficoTortaSin = new GraficoTorta("Sin Pool");
        graficoTortaCon = new GraficoTorta("Con Pool");
        areaLogs = new TextArea();
        areaLogs.setEditable(false);
        areaLogs.setPrefHeight(150);
        areaLogs.setStyle("-fx-control-inner-background: #1e1e2f; -fx-text-fill: #d4b5ff; -fx-font-family: monospace;");

        String css = """
            .root {
                -fx-background-color: linear-gradient(to bottom, #2b1a3a, #3c2a4d);
                -fx-font-family: 'Segoe UI', 'Arial', sans-serif;
                -fx-padding: 20;
            }
            .label {
                -fx-text-fill: #f0e6ff;
                -fx-font-size: 13px;
                -fx-font-weight: bold;
            }
            .title-label {
                -fx-font-size: 28px;
                -fx-text-fill: #ffb3d9;
                -fx-font-weight: bold;
                -fx-effect: dropshadow(gaussian, #c77dff, 10, 0.3, 0, 2);
            }
            .text-field {
                -fx-background-color: #3a2a4a;
                -fx-text-fill: #ffffff;
                -fx-border-color: #d4b5ff;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
                -fx-font-size: 13px;
                -fx-padding: 6 10;
            }
            .button {
                -fx-background-color: linear-gradient(to bottom, #c77dff, #a64dff);
                -fx-text-fill: #ffffff;
                -fx-font-size: 13px;
                -fx-font-weight: bold;
                -fx-background-radius: 8;
                -fx-border-radius: 8;
                -fx-padding: 8 16;
                -fx-cursor: hand;
                -fx-effect: dropshadow(gaussian, #c77dff, 8, 0.2, 0, 2);
            }
            .button:hover {
                -fx-background-color: linear-gradient(to bottom, #d9a3ff, #c77dff);
            }
            .progress-bar {
                -fx-accent: #ffb3d9;
                -fx-background-radius: 10;
                -fx-border-radius: 10;
                -fx-pref-height: 18;
            }
            .progress-bar .track {
                -fx-background-color: #3a2a4a;
            }
            .graph-box {
                -fx-background-color: #2a1a38;
                -fx-border-radius: 15;
                -fx-background-radius: 15;
                -fx-padding: 12;
                -fx-effect: dropshadow(gaussian, #c77dff, 8, 0.1, 0, 2);
                -fx-border-color: #d4b5ff;
                -fx-border-width: 1;
            }
            .result-label {
                -fx-text-fill: #ffb3d9;
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-padding: 8 0 0 0;
            }
            .log-area {
                -fx-background-color: #1e1e2f;
                -fx-text-fill: #d4b5ff;
                -fx-border-color: #d4b5ff;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
            }
            """;

        Scene scene = new Scene(crearLayout(), 1100, 900);
        scene.getStylesheets().add("data:text/css," + css.replace("\n", "").replace(" ", " "));
        stage.setScene(scene);
        stage.setTitle("ConexionesPool - Simulación");
        stage.show();

        btnSimularRaw.setOnAction(e -> iniciarSimulacion(TipoSimulacion.RAW));
        btnSimularPool.setOnAction(e -> iniciarSimulacion(TipoSimulacion.POOL));
        btnSimularAmbos.setOnAction(e -> iniciarSimulacion(TipoSimulacion.AMBOS));
        btnFreno.setOnAction(e -> {
            if (freno != null) freno.activar();
            btnFreno.setStyle("-fx-background-color: linear-gradient(to bottom, #ff4b4b, #b91c1c);");
        });
    }

    private enum TipoSimulacion { RAW, POOL, AMBOS }

    private VBox crearLayout() {
        Label titulo = new Label("Pool de Conexiones - Simulador");
        titulo.getStyleClass().add("title-label");

        HBox peticionesBox = new HBox(10, new Label("Número de peticiones:"), txtPeticiones);
        peticionesBox.setAlignment(Pos.CENTER);

        HBox botonesBox = new HBox(10, btnSimularRaw, btnSimularPool, btnSimularAmbos, btnFreno);
        botonesBox.setAlignment(Pos.CENTER);

        graficoTortaSin.setPrefSize(300, 300);
        graficoTortaCon.setPrefSize(300, 300);
        HBox tortasBox = new HBox(40, graficoTortaSin, graficoTortaCon);
        tortasBox.setAlignment(Pos.CENTER);
        tortasBox.setPadding(new Insets(10, 0, 10, 0));

        VBox sinPoolBox = new VBox(5,
                new Label("📊 Sin pool de conexiones"),
                barraSinPool,
                lblEstadoSin
        );
        sinPoolBox.getStyleClass().add("graph-box");
        sinPoolBox.setPrefWidth(400);

        VBox conPoolBox = new VBox(5,
                new Label("⚡ Con pool de conexiones"),
                barraConPool,
                lblEstadoCon
        );
        conPoolBox.getStyleClass().add("graph-box");
        conPoolBox.setPrefWidth(400);

        HBox poolsBox = new HBox(20, sinPoolBox, conPoolBox);
        poolsBox.setAlignment(Pos.CENTER);

        VBox graficaFinalBox = new VBox(graficaFinal);
        graficaFinalBox.getStyleClass().add("graph-box");
        graficaFinalBox.setVisible(false);
        graficaFinalBox.managedProperty().bind(graficaFinalBox.visibleProperty());

        VBox logBox = new VBox(5, new Label("📋 Logs de simulación:"), areaLogs);
        logBox.getStyleClass().add("graph-box");

        lblResumen.getStyleClass().add("result-label");

        VBox root = new VBox(12,
                titulo,
                peticionesBox,
                botonesBox,
                tortasBox,
                poolsBox,
                lblResumen,
                graficaFinalBox,
                logBox
        );
        root.setAlignment(Pos.TOP_CENTER);
        root.getStyleClass().add("root");
        return root;
    }

    private void iniciarSimulacion(TipoSimulacion tipo) {
        btnSimularRaw.setDisable(true);
        btnSimularPool.setDisable(true);
        btnSimularAmbos.setDisable(true);
        btnFreno.setDisable(false);
        graficaFinal.setVisible(false);
        lblResumen.setText("");
        barraSinPool.setProgress(0);
        barraConPool.setProgress(0);
        lblEstadoSin.setText("⏳ Sin pool: en progreso...");
        lblEstadoCon.setText("⏳ Con pool: en progreso...");
        areaLogs.clear();

        graficoTortaSin.limpiar();
        graficoTortaCon.limpiar();

        int numPeticiones;
        try {
            numPeticiones = Integer.parseInt(txtPeticiones.getText());
        } catch (NumberFormatException ex) {
            lblResumen.setText("Número inválido");
            habilitarBotones();
            return;
        }

        if (numPeticiones < 50 || numPeticiones > 40000) {
            lblResumen.setText("El número de peticiones debe estar entre 50 y 40000");
            habilitarBotones();
            return;
        }

        final String url = "jdbc:postgresql://" + config.obtener("DB_HOST") + ":"
                + config.obtener("DB_PORT") + "/" + config.obtener("DB_NAME");
        final String user = config.obtener("DB_USER");
        final String pass = config.obtener("DB_PASSWORD");
        final int tamPool = config.obtenerEntero("POOL_SIZE");
        final int reintentosMax = config.obtenerEntero("REINTENTOS_MAXIMOS");
        final String[] queriesArray = config.obtenerQueries();
        final Supplier<String> proveedorQueries = () -> queriesArray[new Random().nextInt(queriesArray.length)];

        freno = new Freno();
        final LoggerMuestras logger = new LoggerMuestras() {
            @Override
            public void log(String mensaje) {
                Platform.runLater(() -> areaLogs.appendText(mensaje + "\n"));
                super.log(mensaje);
            }
        };

        final PoolConexiones pool = new PoolConexiones(url, user, pass, tamPool);
        final AdministradorPool admin = new AdministradorPool(pool);

        contadorSin = null;
        contadorCon = null;

        timelineSin = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            if (contadorSin != null) {
                int exitosas = contadorSin.getExitosas();
                int fallidas = contadorSin.getFallidas();
                graficoTortaSin.actualizar(exitosas, fallidas);
            }
        }));
        timelineSin.setCycleCount(Timeline.INDEFINITE);

        timelineCon = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            if (contadorCon != null) {
                int exitosas = contadorCon.getExitosas();
                int fallidas = contadorCon.getFallidas();
                graficoTortaCon.actualizar(exitosas, fallidas);
            }
        }));
        timelineCon.setCycleCount(Timeline.INDEFINITE);

        final int[] simulacionesActivas = {0};

        if (tipo == TipoSimulacion.RAW || tipo == TipoSimulacion.AMBOS) {
            simulacionesActivas[0]++;
            contadorSin = new ContadorEstadisticas();
            final ContadorEstadisticas contadorSinFinal = contadorSin;
            Thread hiloContadorSin = new Thread(contadorSinFinal);
            hiloContadorSin.start();

            SimuladorRaw simuladorRaw = new SimuladorRaw(
                    numPeticiones, reintentosMax, proveedorQueries,
                    freno, logger, url, user, pass
            );

            Thread hiloRaw = new Thread(() -> {
                simuladorRaw.ejecutar(contadorSinFinal, progreso -> {
                    Platform.runLater(() -> barraSinPool.setProgress(progreso));
                });

                contadorSinFinal.detener();
                try { hiloContadorSin.join(); } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }

                Platform.runLater(() -> {
                    int exitosas = contadorSinFinal.getExitosas();
                    int fallidas = contadorSinFinal.getFallidas();
                    double pct = contadorSinFinal.getPorcentajeExito();
                    lblEstadoSin.setText(String.format("✅ Sin pool: %d exitosas, %d fallidas (%.2f%%)", exitosas, fallidas, pct));
                    graficoTortaSin.actualizar(exitosas, fallidas);
                });

                synchronized (simulacionesActivas) {
                    simulacionesActivas[0]--;
                    if (simulacionesActivas[0] == 0) {
                        Platform.runLater(() -> {
                            timelineSin.stop();
                            timelineCon.stop();
                            habilitarBotones();
                        });
                    }
                }
            });
            hiloRaw.start();
        }

        if (tipo == TipoSimulacion.POOL || tipo == TipoSimulacion.AMBOS) {
            simulacionesActivas[0]++;
            contadorCon = new ContadorEstadisticas();
            final ContadorEstadisticas contadorConFinal = contadorCon;
            Thread hiloContadorCon = new Thread(contadorConFinal);
            hiloContadorCon.start();

            SimuladorPool simuladorPool = new SimuladorPool(
                    numPeticiones, reintentosMax, proveedorQueries,
                    freno, logger, admin
            );

            Thread hiloPool = new Thread(() -> {
                simuladorPool.ejecutar(contadorConFinal, progreso -> {
                    Platform.runLater(() -> barraConPool.setProgress(progreso));
                });

                contadorConFinal.detener();
                try { hiloContadorCon.join(); } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }

                Platform.runLater(() -> {
                    int exitosas = contadorConFinal.getExitosas();
                    int fallidas = contadorConFinal.getFallidas();
                    double pct = contadorConFinal.getPorcentajeExito();
                    lblEstadoCon.setText(String.format("⚡ Con pool: %d exitosas, %d fallidas (%.2f%%)", exitosas, fallidas, pct));
                    graficoTortaCon.actualizar(exitosas, fallidas);
                });

                synchronized (simulacionesActivas) {
                    simulacionesActivas[0]--;
                    if (simulacionesActivas[0] == 0) {
                        Platform.runLater(() -> {
                            timelineSin.stop();
                            timelineCon.stop();
                            habilitarBotones();
                        });
                    }
                }
            });
            hiloPool.start();
        }

        if (tipo == TipoSimulacion.RAW || tipo == TipoSimulacion.AMBOS) {
            timelineSin.play();
        }
        if (tipo == TipoSimulacion.POOL || tipo == TipoSimulacion.AMBOS) {
            timelineCon.play();
        }
    }

    private void habilitarBotones() {
        btnSimularRaw.setDisable(false);
        btnSimularPool.setDisable(false);
        btnSimularAmbos.setDisable(false);
        btnFreno.setDisable(true);
        btnFreno.setStyle("");
    }
}