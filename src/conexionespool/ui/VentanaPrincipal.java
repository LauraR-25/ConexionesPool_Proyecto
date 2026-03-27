package conexionespool.ui;

import conexionespool.componentes.DBComponent;
import conexionespool.componentes.DBComponentConnector;
import conexionespool.componentes.DBComponentRegistry;
import conexionespool.componentes.DBException;
import conexionespool.componentes.DBQueryId;
import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.simulacion.Simulador;
import conexionespool.simulacion.SimuladorRaw;
import conexionespool.util.ConfiguracionEntorno;
import conexionespool.util.Freno;
import conexionespool.adaptadores.DatabaseType;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.sql.*;
import java.util.EnumMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VentanaPrincipal extends Application {

    // UI components
    private TextField txtPeticiones;
    private ProgressBar progressRaw, progressPool;
    private Label lblEstadoRaw, lblEstadoPool, lblResumen;
    private GraficoTorta graficaRaw, graficaPool;
    private Button btnSimular, btnFreno;
    private ComboBox<DatabaseType> cmbDatabase;
    private Label estadoConexion;

    // Connection fields
    private TextField txtHost, txtPort, txtDb, txtUser;
    private PasswordField txtPass;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final DBComponentConnector connector = new DBComponentConnector();
    private final Freno freno = new Freno();
    private final EnumMap<DatabaseType, conexionespool.componentes.ConnectionConfig> connectionConfigs =
            new EnumMap<>(DatabaseType.class);
    private ConfiguracionEntorno envConfig;

    // Progresso suavizado
    private double targetProgresoRaw = 0, shownProgresoRaw = 0;
    private double targetProgresoPool = 0, shownProgresoPool = 0;
    private AnimationTimer smoothTimer;

    private final Label errorMsg = new Label("");
    private volatile boolean simulacionEnCurso = false;

    @Override
    public void start(Stage stage) {
        envConfig = new ConfiguracionEntorno(".env");
        String host = envConfig.obtener("DB_HOST");
        String port = envConfig.obtener("DB_PORT");
        String db = envConfig.obtener("DB_NAME");
        String user = envConfig.obtener("DB_USER");
        String pass = envConfig.obtener("DB_PASSWORD");

        // --- Configurar interfaz ---
        HBox root = new HBox(25);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("main-root");

        // Columna izquierda (configuración)
        VBox leftCol = crearPanelConfiguracion(host, port, db, user, pass);
        ScrollPane leftScroll = new ScrollPane(leftCol);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setPrefViewportWidth(330);
        leftScroll.setMinWidth(330);
        leftScroll.setMaxWidth(330);
        leftScroll.getStyleClass().add("left-scroll");

        // Columna derecha (métricas y gráficas)
        VBox rightCol = crearPanelMetricas();

        root.getChildren().addAll(leftScroll, rightCol);

        Scene scene = new Scene(root, 1200, 800);
        aplicarCSS(scene);

        stage.setTitle("ConexionesPool - Simulación (Raw vs Pool)");
        stage.setScene(scene);
        stage.show();

        startSmoothProgressAnimation();
        aplicarDefaultsMotorSeleccionado();

        btnSimular.setOnAction(_ -> ejecutarSimulacion());
    }

    // Crea el panel de configuración con campos para host, puerto, base de datos, usuario, contraseña,etc
    private VBox crearPanelConfiguracion(String host, String port, String db, String user, String pass) {
        VBox leftCol = new VBox(12);
        leftCol.setMinWidth(320);
        leftCol.setPrefWidth(320);
        leftCol.setMaxWidth(320);
        HBox.setHgrow(leftCol, Priority.NEVER);
        leftCol.getStyleClass().add("panel-oscuro");
        leftCol.setAlignment(Pos.TOP_CENTER);
        leftCol.setFillWidth(true);

        Label titleLeft = new Label("Configuración");
        titleLeft.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titleLeft.setTextFill(Color.WHITE);
        titleLeft.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Selector de base de datos
        Label lblDB = new Label("Motor de Base de Datos");
        lblDB.setTextFill(Color.LIGHTGRAY);
        cmbDatabase = new ComboBox<>();
        cmbDatabase.getItems().addAll(DatabaseType.POSTGRES, DatabaseType.MYSQL, DatabaseType.H2);
        cmbDatabase.setValue(DatabaseType.POSTGRES);
        cmbDatabase.getStyleClass().add("custom-field");
        cmbDatabase.setMaxWidth(Double.MAX_VALUE);
        cmbDatabase.setOnAction(_ -> aplicarDefaultsMotorSeleccionado());

        Label lblEstado = new Label("Estado de conexión");
        lblEstado.setTextFill(Color.LIGHTGRAY);
        estadoConexion = new Label("Desconectado");
        estadoConexion.setWrapText(true);
        estadoConexion.setMaxWidth(Double.MAX_VALUE);
        estadoConexion.setTextFill(Color.web("#ff4e8e"));

        Label lblConn = new Label("Datos de conexión");
        lblConn.setTextFill(Color.LIGHTGRAY);

        txtHost = new TextField(host);
        txtHost.setPromptText("host (ej. localhost)");
        txtHost.getStyleClass().add("custom-field");

        txtPort = new TextField(port);
        txtPort.setPromptText("puerto (ej. 5432/3306)");
        txtPort.getStyleClass().add("custom-field");

        txtDb = new TextField(db);
        txtDb.setPromptText("base de datos");
        txtDb.getStyleClass().add("custom-field");

        txtUser = new TextField(user);
        txtUser.setPromptText("usuario");
        txtUser.getStyleClass().add("custom-field");

        txtPass = new PasswordField();
        txtPass.setText(pass);
        txtPass.setPromptText("contraseña");
        txtPass.getStyleClass().add("custom-field");

        Button btnConectar = new Button("⛓ Conectar");
        btnConectar.getStyleClass().add("btn-iniciar");
        btnConectar.setMaxWidth(Double.MAX_VALUE);
        btnConectar.setOnAction(_ -> conectarDB(
                txtHost.getText().trim(),
                txtPort.getText().trim(),
                txtDb.getText().trim(),
                txtUser.getText().trim(),
                txtPass.getText()
        ));

        Button btnLimpiarConexion = new Button("🧹 Limpiar conexión");
        btnLimpiarConexion.getStyleClass().add("btn-freno");
        btnLimpiarConexion.setMaxWidth(Double.MAX_VALUE);
        btnLimpiarConexion.setOnAction(_ -> limpiarConexion());

        Label lblPet = new Label("Número de Peticiones (1-40000)");
        lblPet.setTextFill(Color.LIGHTGRAY);
        txtPeticiones = new TextField("10000");
        txtPeticiones.getStyleClass().add("custom-field");

        btnSimular = new Button("▶ Iniciar simulación");
        btnSimular.getStyleClass().add("btn-iniciar");
        btnSimular.setMaxWidth(Double.MAX_VALUE);
        btnSimular.setDisable(true);

        btnFreno = new Button("■ Alto de emergencia");
        btnFreno.getStyleClass().add("btn-freno");
        btnFreno.setMaxWidth(Double.MAX_VALUE);
        btnFreno.setOnAction(_ -> {
            freno.activar();
            btnFreno.setStyle("-fx-background-color: #8b0000;");
        });

        errorMsg.setTextFill(Color.web("#ff4e8e"));
        errorMsg.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        errorMsg.setWrapText(true);
        errorMsg.setMaxWidth(Double.MAX_VALUE);
        errorMsg.setAlignment(Pos.CENTER);
        errorMsg.setPadding(new Insets(8, 0, 0, 0));

        leftCol.getChildren().addAll(
                titleLeft,
                lblDB,
                cmbDatabase,
                lblEstado,
                estadoConexion,
                lblConn,
                txtHost,
                txtPort,
                txtDb,
                txtUser,
                txtPass,
                btnConectar,
                btnLimpiarConexion,
                lblPet,
                txtPeticiones,
                btnSimular,
                btnFreno,
                errorMsg
        );
        return leftCol;
    }

    // Crea el panel de métricas con tarjetas para Raw y Pool, gráficas de torta y un resumen final.
    private VBox crearPanelMetricas() {
        VBox rightCol = new VBox(20);
        HBox.setHgrow(rightCol, Priority.ALWAYS);
        rightCol.getStyleClass().add("panel-metriz");
        rightCol.setPadding(new Insets(20));

        Label titleRight = new Label("Métricas de Rendimiento");
        titleRight.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleRight.setTextFill(Color.web("#ffb3d9"));

        // Tarjetas Raw y Pool lado a lado
        HBox cardsBox = new HBox(20);
        cardsBox.setAlignment(Pos.CENTER);

        progressRaw = new ProgressBar(0);
        lblEstadoRaw = new Label("Raw: esperando...");
        VBox cardRaw = crearTarjetaKPI("Sin pool (Raw)", lblEstadoRaw, progressRaw, "#4CAF50");
        HBox.setHgrow(cardRaw, Priority.ALWAYS);

        progressPool = new ProgressBar(0);
        lblEstadoPool = new Label("Pool: esperando...");
        VBox cardPool = crearTarjetaKPI("Con pool (Pooled)", lblEstadoPool, progressPool, "#ffb3d9");
        HBox.setHgrow(cardPool, Priority.ALWAYS);

        cardsBox.getChildren().addAll(cardRaw, cardPool);

        // Gráficas de torta (una por cada tipo)
        HBox graphsBox = new HBox(40);
        graphsBox.setAlignment(Pos.CENTER);
        graphsBox.setPadding(new Insets(10, 0, 10, 0));

        graficaRaw = new GraficoTorta("Raw");
        graficaPool = new GraficoTorta("Pool");
        graphsBox.getChildren().addAll(graficaRaw, graficaPool);

        lblResumen = new Label();
        lblResumen.getStyleClass().add("result-label");
        lblResumen.setTextFill(Color.web("#ffb3d9"));
        lblResumen.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));

        rightCol.getChildren().addAll(titleRight, cardsBox, graphsBox, lblResumen);
        return rightCol;
    }

    // Crea una tarjeta de KPI con título, estado, barra de progreso y color personalizado.
    private VBox crearTarjetaKPI(String titulo, Label estado, ProgressBar pb, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 15; -fx-border-color: " + color + "; -fx-border-radius: 15;");
        card.setMinWidth(280);

        Label t = new Label(titulo);
        t.setTextFill(Color.LIGHTGRAY);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        t.setWrapText(true);
        t.setMaxWidth(Double.MAX_VALUE);
        t.setAlignment(Pos.CENTER);

        estado.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        estado.setTextFill(Color.web(color));
        estado.setWrapText(true);
        estado.setAlignment(Pos.CENTER);

        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setStyle("-fx-accent: " + color + ";");

        card.getChildren().addAll(t, pb, estado);
        return card;
    }

    private void startSmoothProgressAnimation() {
        smoothTimer = new AnimationTimer() {
            private static final double ALPHA = 0.18;
            @Override
            public void handle(long now) {
                shownProgresoRaw += (targetProgresoRaw - shownProgresoRaw) * ALPHA;
                if (Math.abs(targetProgresoRaw - shownProgresoRaw) < 0.001) shownProgresoRaw = targetProgresoRaw;
                progressRaw.setProgress(clamp01(shownProgresoRaw));

                shownProgresoPool += (targetProgresoPool - shownProgresoPool) * ALPHA;
                if (Math.abs(targetProgresoPool - shownProgresoPool) < 0.001) shownProgresoPool = targetProgresoPool;
                progressPool.setProgress(clamp01(shownProgresoPool));
            }
            private double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
        };
        smoothTimer.start();
    }

    private void aplicarCSS(Scene scene) {
        String style = """
            .main-root { -fx-background-color: #1e1e2f; }
            .panel-oscuro { -fx-background-color: #2b1a3a; -fx-background-radius: 20; -fx-padding: 16; }
            .panel-metriz { -fx-background-color: #1e1e2f; -fx-border-color: #a88ff0; -fx-border-radius: 20; -fx-border-width: 2; }
            .custom-field { -fx-background-color: #3a2a4a; -fx-text-fill: white; -fx-border-color: #a88ff0; -fx-border-radius: 5; -fx-alignment: center; -fx-font-size: 14; -fx-padding: 6 8 6 8; }
            .btn-iniciar { -fx-background-color: linear-gradient(to bottom, #c77dff, #a64dff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 9; }
            .btn-freno { -fx-background-color: #8b0000; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 9; }
            .left-scroll { -fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0; }
            .left-scroll > .viewport { -fx-background-color: transparent; }
            .result-label { -fx-text-fill: #ffb3d9; -fx-font-size: 16px; -fx-font-weight: bold; }
            """;
        scene.getStylesheets().add("data:text/css," + style.replace("\n", ""));
    }

    // Método para conectar a la base de datos usando los valores ingresados y el DBComponentConnector
    private void conectarDB(String host, String portTxt, String db, String user, String pass) {
        DatabaseType tipo = cmbDatabase.getValue();

        boolean requiereHostPuerto = tipo != DatabaseType.H2;
        if ((requiereHostPuerto && (host.isEmpty() || portTxt.isEmpty())) || db.isEmpty() || user.isEmpty()) {
            errorMsg.setText("Completa todos los campos");
            return;
        }
        int port;
        try {
            port = requiereHostPuerto ? Integer.parseInt(portTxt) : 0;
        } catch (NumberFormatException e) {
            errorMsg.setText("Puerto inválido");
            return;
        }

        try {
            DBComponentRegistry.clear(tipo);
            DBComponentConnector.ConnectResult result = connector.connect(
                    tipo, host, port, db, user, pass);
            DBComponentRegistry.put(result.type(), result.component());
            connectionConfigs.put(tipo, result.config());
            estadoConexion.setText(tipo + ": conectado");
            estadoConexion.setTextFill(Color.web("#7CFC00"));
            errorMsg.setText("Conectado correctamente a " + tipo);
            errorMsg.setTextFill(Color.web("#7CFC00"));
            btnSimular.setDisable(false);
        } catch (DBException e) {
            estadoConexion.setText(tipo + ": error");
            estadoConexion.setTextFill(Color.web("#ff4e8e"));
            errorMsg.setText("Error: " + e.getMessage());
            errorMsg.setTextFill(Color.web("#ff4e8e"));
            btnSimular.setDisable(true);
        }
    }

    private void limpiarConexion() {
        DatabaseType tipo = cmbDatabase.getValue();
        DBComponentRegistry.clear(tipo);
        connectionConfigs.remove(tipo);
        estadoConexion.setText(tipo + ": desconectado");
        estadoConexion.setTextFill(Color.web("#ff4e8e"));
        btnSimular.setDisable(true);
        errorMsg.setText("Conexión limpiada");
        errorMsg.setTextFill(Color.web("#b6aaff"));
    }

    private void ejecutarSimulacion() {
        if (simulacionEnCurso) {
            errorMsg.setText("Ya hay una simulación en curso");
            return;
        }

        DatabaseType tipo = cmbDatabase.getValue();
        if (!DBComponentRegistry.isConnected(tipo)) {
            errorMsg.setText("Conecta a " + tipo + " primero");
            return;
        }

        int num;
        try {
            num = Integer.parseInt(txtPeticiones.getText());
        } catch (NumberFormatException ex) {
            errorMsg.setText("Número inválido");
            return;
        }
        if (num < 1 || num > 40000) {
            errorMsg.setText("Debe estar entre 1 y 40000");
            return;
        }

        final String host = txtHost.getText().trim();
        final String port = txtPort.getText().trim();
        final String db = txtDb.getText().trim();
        final String user = txtUser.getText().trim();
        final String pass = txtPass.getText();

        // Reset UI
        Platform.runLater(() -> {
            simulacionEnCurso = true;
            btnSimular.setDisable(true);
            cmbDatabase.setDisable(true);
            targetProgresoRaw = 0;
            targetProgresoPool = 0;
            lblEstadoRaw.setText("Raw: en progreso...");
            lblEstadoPool.setText("Pool: en progreso...");
            graficaRaw.limpiar();
            graficaPool.limpiar();
            lblResumen.setText("");
            errorMsg.setText("");
        });

        new Thread(() -> {
            try {
                freno.desactivar();

            conexionespool.componentes.ConnectionConfig config = connectionConfigs.get(tipo);
            if (config == null) {
                throw new IllegalStateException("No hay configuración de conexión activa. Presiona Conectar antes de iniciar.");
            }

            // Raw debe usar la misma URL/credenciales ya validadas por el conector.
            String url = config.url();
            String rawUser = config.user();
            String rawPass = config.password();

            if (tipo == DatabaseType.POSTGRES) {
                url = appendIfMissing(url, "connectTimeout=1");
                url = appendIfMissing(url, "socketTimeout=2");
            } else if (tipo == DatabaseType.MYSQL) {
                url = appendIfMissing(url, "connectTimeout=1000");
                url = appendIfMissing(url, "socketTimeout=2000");
            }
            String query = "SELECT * FROM usuario LIMIT 1";
            int reintentos = 1;
            try {
                ConfiguracionEntorno conf = new ConfiguracionEntorno(".env");
                String rawRetries = conf.obtener("REINTENTOS_MAXIMOS");
                if (rawRetries != null && !rawRetries.isBlank()) {
                    reintentos = Math.max(0, Integer.parseInt(rawRetries.trim()));
                }
            } catch (Exception ignored) {
                // Si .env no está disponible o es inválido, se mantiene el valor por defecto.
            }

            // Contadores y simuladores
            ContadorEstadisticas contadorRaw = new ContadorEstadisticas();
            ContadorEstadisticas contadorPool = new ContadorEstadisticas();

            Thread hiloContadorRaw = new Thread(contadorRaw);
            Thread hiloContadorPool = new Thread(contadorPool);
            hiloContadorRaw.start();
            hiloContadorPool.start();

            SimuladorRaw simuladorRaw = new SimuladorRaw(num, reintentos, () -> query, freno, url, rawUser, rawPass);
            Simulador simuladorPool = new Simulador(num, reintentos, () -> new DBQueryId("usuario.selectOne"), freno, tipo);

            CountDownLatch terminadoRaw = new CountDownLatch(1);
            CountDownLatch terminadoPool = new CountDownLatch(1);

            final ScheduledFuture<?>[] futureRaw = new ScheduledFuture<?>[1];
            final ScheduledFuture<?>[] futurePool = new ScheduledFuture<?>[1];
            try {
                futureRaw[0] = scheduler.scheduleAtFixedRate(() -> {
                    int completadas = simuladorRaw.getCompletadas();
                    double progreso = completadas / (double) num;
                    Platform.runLater(() -> targetProgresoRaw = Math.min(progreso, 1.0));
                    if (completadas >= num || freno.estaActivado()) {
                        if (futureRaw[0] != null) futureRaw[0].cancel(false);
                        terminadoRaw.countDown();
                    }
                }, 0, 20, TimeUnit.MILLISECONDS);

                futurePool[0] = scheduler.scheduleAtFixedRate(() -> {
                    int completadas = simuladorPool.getCompletadas();
                    double progreso = completadas / (double) num;
                    Platform.runLater(() -> targetProgresoPool = Math.min(progreso, 1.0));
                    if (completadas >= num || freno.estaActivado()) {
                        if (futurePool[0] != null) futurePool[0].cancel(false);
                        terminadoPool.countDown();
                    }
                }, 0, 20, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }

            CountDownLatch disparoSimultaneo = new CountDownLatch(1);
            Thread hiloRaw = new Thread(() -> {
                simuladorRaw.ejecutar(contadorRaw, p -> {}, disparoSimultaneo);
            });
            Thread hiloPool = new Thread(() -> {
                simuladorPool.ejecutarConPool(contadorPool, p -> {}, disparoSimultaneo);
            });
            hiloRaw.start();
            hiloPool.start();
            disparoSimultaneo.countDown();

            try {
                hiloRaw.join();
                hiloPool.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            contadorRaw.detener();
            contadorPool.detener();
            try {
                hiloContadorRaw.join();
                hiloContadorPool.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                terminadoRaw.await(2, TimeUnit.SECONDS);
                terminadoPool.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            int exitosasRaw = contadorRaw.getExitosas();
            int fallidasRaw = contadorRaw.getFallidas();
            double pctRaw = contadorRaw.getPorcentajeExito();

            int exitosasPool = contadorPool.getExitosas();
            int fallidasPool = contadorPool.getFallidas();
            double pctPool = contadorPool.getPorcentajeExito();

            Platform.runLater(() -> {
                lblEstadoRaw.setText(String.format("Raw: %d ok / %d fail | %.2f%% éxito", exitosasRaw, fallidasRaw, pctRaw));
                lblEstadoPool.setText(String.format("Pool: %d ok / %d fail | %.2f%% éxito", exitosasPool, fallidasPool, pctPool));
                graficaRaw.actualizar(exitosasRaw, fallidasRaw);
                graficaPool.actualizar(exitosasPool, fallidasPool);

                String mejor = (pctRaw > pctPool) ? "SIN POOL (Raw)" : (pctPool > pctRaw) ? "CON POOL (Pooled)" : "EMPATE";
                lblResumen.setText("🏆 Mejor rendimiento: " + mejor);
            });
            } catch (Exception e) {
                Platform.runLater(() -> errorMsg.setText("Error en simulación: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                btnSimular.setDisable(false);
                cmbDatabase.setDisable(false);
                simulacionEnCurso = false;
                });
            }
        }).start();
    }

    private void aplicarDefaultsMotorSeleccionado() {
        DatabaseType tipo = cmbDatabase.getValue();
        if (tipo == null || envConfig == null) {
            return;
        }

        switch (tipo) {
            case POSTGRES -> {
                txtHost.setText(valorEnv("DB_HOST", "localhost"));
                txtPort.setText(valorEnv("DB_PORT", "5432"));
                txtDb.setText(valorEnv("DB_NAME", "javaprueba"));
                txtUser.setText(valorEnv("DB_USER", "postgres"));
                txtPass.setText(valorEnv("DB_PASSWORD", ""));
            }
            case MYSQL -> {
                txtHost.setText(valorEnv("MYSQL_HOST", "localhost"));
                txtPort.setText(valorEnv("MYSQL_PORT", "3306"));
                txtDb.setText(valorEnv("MYSQL_DB", "javaprueba"));
                txtUser.setText(valorEnv("MYSQL_USER", "root"));
                txtPass.setText(valorEnv("MYSQL_PASSWORD", ""));
            }
            case H2 -> {
                txtHost.setText("");
                txtPort.setText("0");
                txtDb.setText(valorEnv("H2_DB", valorEnv("DB_NAME", "javaprueba")));
                txtUser.setText(valorEnv("H2_USER", "sa"));
                txtPass.setText(valorEnv("H2_PASSWORD", ""));
            }
        }
    }

    private String valorEnv(String key, String fallback) {
        String value = envConfig.obtener(key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String appendIfMissing(String url, String param) {
        if (url == null || url.isBlank() || param == null || param.isBlank()) {
            return url;
        }
        String key = param.substring(0, param.indexOf('='));
        if (url.contains(key + "=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + param;
    }

}