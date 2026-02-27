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
import javafx.geometry.Insets;
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
    private GraficoBarras graficaFinal;
    private GraficoTorta graficoTortaSin, graficoTortaCon;
    private TextArea areaLogs;
    private Button btnSimular, btnFreno;
    private ConfiguracionEntorno config;
    private Freno freno;

    @Override
    public void start(Stage stage) {
        config = new ConfiguracionEntorno(".env");

        // Componentes
        txtPeticiones = new TextField(String.valueOf(config.obtenerEntero("PETICIONES_POR_DEFECTO")));
        btnSimular = new Button("🚀 Iniciar simulación");
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

        // CSS embebido
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

        Scene scene = new Scene(crearLayout(), 1100, 850);
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

        // Gráficas de torta
        graficoTortaSin.setPrefSize(300, 300);
        graficoTortaCon.setPrefSize(300, 300);
        HBox tortasBox = new HBox(40, graficoTortaSin, graficoTortaCon);
        tortasBox.setAlignment(Pos.CENTER);
        tortasBox.setPadding(new Insets(10, 0, 10, 0));

        // Cuadros de Sin pool y Con pool
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

        // Gráfica final de barras (opcional)
        VBox graficaFinalBox = new VBox(graficaFinal);
        graficaFinalBox.getStyleClass().add("graph-box");
        graficaFinalBox.setVisible(false);
        graficaFinalBox.managedProperty().bind(graficaFinalBox.visibleProperty());

        // Área de logs
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

    private void iniciarSimulacion() {
        btnSimular.setDisable(true);
        btnFreno.setDisable(false);
        graficaFinal.setVisible(false);
        lblResumen.setText("");
        barraSinPool.setProgress(0);
        barraConPool.setProgress(0);
        lblEstadoSin.setText("⏳ Sin pool: en progreso...");
        lblEstadoCon.setText("⏳ Con pool: en progreso...");
        areaLogs.clear();

        // Limpiar gráficas de torta
        graficoTortaSin.limpiar();
        graficoTortaCon.limpiar();

        int numPeticiones;
        try {
            numPeticiones = Integer.parseInt(txtPeticiones.getText());
        } catch (NumberFormatException ex) {
            lblResumen.setText("Número inválido");
            btnSimular.setDisable(false);
            return;
        }

        // Validar rango de peticiones (mínimo 50, máximo 40000)
        if (numPeticiones < 50 || numPeticiones > 40000) {
            lblResumen.setText("El número de peticiones debe estar entre 50 y 40000");
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
        LoggerMuestras logger = new LoggerMuestras() {
            @Override
            public void log(String mensaje) {
                Platform.runLater(() -> areaLogs.appendText(mensaje + "\n"));
                super.log(mensaje);
            }
        };

        PoolConexiones pool = new PoolConexiones(url, user, pass, tamPool, timeout);
        AdministradorPool admin = new AdministradorPool(pool);

        // Lanzar ambas simulaciones en hilos separados (paralelo)
        Thread hiloRaw = new Thread(() -> {
            ejecutarSimulacionRaw(numPeticiones, reintentosMax, proveedorQueries, freno, logger, url, user, pass);
        });
        Thread hiloPool = new Thread(() -> {
            ejecutarSimulacionPool(numPeticiones, reintentosMax, proveedorQueries, freno, logger, admin);
        });

        hiloRaw.start();
        hiloPool.start();

        // Hilo que espera a que terminen y luego habilita botón (opcional)
        new Thread(() -> {
            try {
                hiloRaw.join();
                hiloPool.join();
                Platform.runLater(() -> {
                    btnSimular.setDisable(false);
                    btnFreno.setDisable(true);
                    btnFreno.setStyle("");
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void ejecutarSimulacionRaw(int numPeticiones, int reintentosMax, Supplier<String> proveedorQueries,
                                       Freno freno, LoggerMuestras logger, String url, String user, String pass) {
        ContadorEstadisticas contador = new ContadorEstadisticas();
        Thread hiloContador = new Thread(contador);
        hiloContador.start();

        SimuladorRaw simulador = new SimuladorRaw(
                numPeticiones, reintentosMax, proveedorQueries,
                freno, logger, url, user, pass
        );

        simulador.ejecutar(contador, progreso -> {
            Platform.runLater(() -> {
                barraSinPool.setProgress(progreso);
            });
        });

        contador.detener();
        try { hiloContador.join(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitosas = contador.getExitosas();
        int fallidas = contador.getFallidas();
        double pct = contador.getPorcentajeExito();

        Platform.runLater(() -> {
            lblEstadoSin.setText(String.format("✅ Sin pool: %d exitosas, %d fallidas (%.2f%%)", exitosas, fallidas, pct));
            graficoTortaSin.actualizar(exitosas, fallidas);
        });
    }

    private void ejecutarSimulacionPool(int numPeticiones, int reintentosMax, Supplier<String> proveedorQueries,
                                        Freno freno, LoggerMuestras logger, AdministradorPool admin) {
        ContadorEstadisticas contador = new ContadorEstadisticas();
        Thread hiloContador = new Thread(contador);
        hiloContador.start();

        SimuladorPool simulador = new SimuladorPool(
                numPeticiones, reintentosMax, proveedorQueries,
                freno, logger, admin
        );

        simulador.ejecutar(contador, progreso -> {
            Platform.runLater(() -> {
                barraConPool.setProgress(progreso);
            });
        });

        contador.detener();
        try { hiloContador.join(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitosas = contador.getExitosas();
        int fallidas = contador.getFallidas();
        double pct = contador.getPorcentajeExito();

        Platform.runLater(() -> {
            lblEstadoCon.setText(String.format("⚡ Con pool: %d exitosas, %d fallidas (%.2f%%)", exitosas, fallidas, pct));
            graficoTortaCon.actualizar(exitosas, fallidas);
        });
    }
}