package conexionespool.ui;

import conexionespool.componentes.DBComponent;
import conexionespool.componentes.DBComponentConnector;
import conexionespool.componentes.DBComponentRegistry;
import conexionespool.componentes.DBException;
import conexionespool.componentes.DBQueryId;
import conexionespool.modelo.ContadorEstadisticas;
import conexionespool.modelo.Resultado;
import conexionespool.simulacion.Simulador;
import conexionespool.util.ConfiguracionEntorno;
import conexionespool.util.DatabaseType;
import conexionespool.util.Freno;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VentanaPrincipal extends Application {

    private final VBox panelGrafica = new VBox();
    private final Label statsPool = new Label("0% Éxito");
    private final ProgressBar progressPool = new ProgressBar(0);
    private TextField txtPeticiones;
    private RadioButton rbPostgres;
    private Label estadoPostgres;
    private Button btnSimular;

    // Campos conexión
    private TextField txtHost, txtPort, txtDb, txtUser;
    private PasswordField txtPass;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final DBComponentConnector connector = new DBComponentConnector();
    private final Freno freno = new Freno();

    // Progreso objetivo y suavizado
    private double targetProgresoPool = 0;
    private double shownProgresoPool = 0;
    private AnimationTimer smoothTimer;

    private final Label errorMsg = new Label("");

    @Override
    public void start(Stage stage) {
        // --- Cargar configuración desde .env ---
        ConfiguracionEntorno config = new ConfiguracionEntorno(".env");
        String host = config.obtener("DB_HOST");
        String port = config.obtener("DB_PORT");
        String db = config.obtener("DB_NAME");
        String user = config.obtener("DB_USER");
        String pass = config.obtener("DB_PASSWORD");

        // --- CONTENEDOR PRINCIPAL ---
        HBox root = new HBox(25);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("main-root");

        // ================= COLUMNA IZQUIERDA (Configuración) =================
        VBox leftCol = new VBox(12);
        leftCol.setMinWidth(320);
        leftCol.setPrefWidth(320);
        leftCol.setMaxWidth(320);
        HBox.setHgrow(leftCol, Priority.NEVER);
        leftCol.getStyleClass().add("panel-oscuro");
        leftCol.setAlignment(Pos.TOP_CENTER);
        leftCol.setFillWidth(true);

        Label titleLeft = new Label("Configuración de\nParámetros");
        titleLeft.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titleLeft.setTextFill(Color.WHITE);
        titleLeft.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Selector DB (solo PostgreSQL)
        rbPostgres = new RadioButton("PostgreSQL");
        rbPostgres.setSelected(true);
        rbPostgres.getStyleClass().add("db-selector");
        HBox dbContainer = new HBox(12, rbPostgres);
        dbContainer.setAlignment(Pos.CENTER);

        Label lblDb = new Label("Base de Datos");
        lblDb.setTextFill(Color.LIGHTGRAY);

        Label lblEstado = new Label("Estado de conexión");
        lblEstado.setTextFill(Color.LIGHTGRAY);

        estadoPostgres = new Label("PostgreSQL: desconectado");
        estadoPostgres.setWrapText(true);
        estadoPostgres.setMaxWidth(Double.MAX_VALUE);
        estadoPostgres.setTextFill(Color.web("#ff4e8e"));

        // ================= FORMULARIO CONEXIÓN =================
        Label lblConn = new Label("Datos de conexión");
        lblConn.setTextFill(Color.LIGHTGRAY);

        txtHost = new TextField(host);
        txtHost.setPromptText("host (ej. localhost)");
        txtHost.getStyleClass().add("custom-field");

        txtPort = new TextField(port);
        txtPort.setPromptText("puerto (ej. 5432)");
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

        Button btnLimpiarConexion = new Button("🧹 Limpiar conexión actual");
        btnLimpiarConexion.getStyleClass().add("btn-freno");
        btnLimpiarConexion.setMaxWidth(Double.MAX_VALUE);

        // Control Peticiones
        Label lblPet = new Label("Número de Peticiones (1-40000)");
        lblPet.setTextFill(Color.LIGHTGRAY);
        txtPeticiones = new TextField("10000");
        txtPeticiones.getStyleClass().add("custom-field");

        // Botones Acción
        btnSimular = new Button("▶ Iniciar simulación");
        btnSimular.getStyleClass().add("btn-iniciar");
        btnSimular.setMaxWidth(Double.MAX_VALUE);

        Button btnFreno = new Button("■ Alto de emergencia");
        btnFreno.getStyleClass().add("btn-freno");
        btnFreno.setMaxWidth(Double.MAX_VALUE);

        errorMsg.setTextFill(Color.web("#ff4e8e"));
        errorMsg.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        errorMsg.setWrapText(true);
        errorMsg.setMaxWidth(Double.MAX_VALUE);
        errorMsg.setAlignment(Pos.CENTER);
        errorMsg.setPadding(new Insets(8, 0, 0, 0));

        leftCol.getChildren().addAll(
                titleLeft,
                dbContainer,
                lblDb,
                lblEstado,
                estadoPostgres,
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

        ScrollPane leftScroll = new ScrollPane(leftCol);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setPrefViewportWidth(330);
        leftScroll.setMinWidth(330);
        leftScroll.setMaxWidth(330);
        leftScroll.getStyleClass().add("left-scroll");

        // ================= COLUMNA DERECHA (Métricas) =================
        VBox rightCol = new VBox(20);
        HBox.setHgrow(rightCol, Priority.ALWAYS);
        rightCol.getStyleClass().add("panel-metriz");
        rightCol.setPadding(new Insets(20));

        Label titleRight = new Label("Métricas de Rendimiento en Tiempo Real");
        titleRight.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleRight.setTextFill(Color.web("#ffb3d9"));

        // Tarjeta KPI principal (solo pool)
        HBox kpiBox = new HBox(15);
        VBox cardPool = crearTarjetaKPI("Simulación con Pool", statsPool, progressPool, "#ffb3d9");
        HBox.setHgrow(cardPool, Priority.ALWAYS);
        kpiBox.getChildren().add(cardPool);

        // Área de Gráficas
        panelGrafica.setAlignment(Pos.CENTER);
        VBox.setVgrow(panelGrafica, Priority.ALWAYS);

        rightCol.getChildren().addAll(titleRight, kpiBox, panelGrafica);
        root.getChildren().addAll(leftScroll, rightCol);

        Scene scene = new Scene(root, 1120, 760);
        aplicarCSS(scene);

        // Conectar automáticamente al inicio con los datos del .env
        Platform.runLater(() -> conectarDB());

        stage.setTitle("ConexionesPool - Simulación");
        stage.setScene(scene);
        stage.show();

        startSmoothProgressAnimation();

        btnSimular.setOnAction(_ -> ejecutarSimulacion());
        btnConectar.setOnAction(_ -> conectarDB());
        btnLimpiarConexion.setOnAction(_ -> limpiarConexionActual());
        btnFreno.setOnAction(_ -> {
            freno.activar();
            statsPool.setText("Freno de emergencia activado");
        });

        stage.setOnCloseRequest(_ -> {
            scheduler.shutdownNow();
            if (smoothTimer != null) smoothTimer.stop();
        });
    }

    private void startSmoothProgressAnimation() {
        smoothTimer = new AnimationTimer() {
            private static final double ALPHA = 0.18;
            @Override
            public void handle(long now) {
                shownProgresoPool += (targetProgresoPool - shownProgresoPool) * ALPHA;
                if (Math.abs(targetProgresoPool - shownProgresoPool) < 0.001) {
                    shownProgresoPool = targetProgresoPool;
                }
                progressPool.setProgress(clamp01(shownProgresoPool));
            }
            private double clamp01(double v) {
                return v < 0 ? 0 : (v > 1 ? 1 : v);
            }
        };
        smoothTimer.start();
    }

    private VBox crearTarjetaKPI(String titulo, Label val, ProgressBar pb, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 15; -fx-border-color: " + color + "; -fx-border-radius: 15;");
        card.setMinWidth(220);
        card.setPrefWidth(260);
        card.setMaxWidth(420);

        Label t = new Label(titulo);
        t.setTextFill(Color.LIGHTGRAY);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        t.setWrapText(true);
        t.setMaxWidth(Double.MAX_VALUE);
        t.setAlignment(Pos.CENTER);

        val.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        val.setTextFill(Color.web(color));
        val.setWrapText(true);
        val.setMaxWidth(400);
        val.setMinWidth(220);
        val.setAlignment(Pos.CENTER);

        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setStyle("-fx-accent: " + color + ";");

        card.getChildren().addAll(t, val, pb);
        return card;
    }

    private void aplicarCSS(Scene scene) {
        String style = """
            .main-root { -fx-background-color: #1e1e2f; }
            .panel-oscuro { -fx-background-color: #2b1a3a; -fx-background-radius: 20; -fx-padding: 16; }
            .panel-metriz { -fx-background-color: #1e1e2f; -fx-border-color: #a88ff0; -fx-border-radius: 20; -fx-border-width: 2; }
            .custom-field { -fx-background-color: #3a2a4a; -fx-text-fill: white; -fx-border-color: #a88ff0; -fx-border-radius: 5; -fx-alignment: center; -fx-font-size: 14; -fx-padding: 6 8 6 8; }
            .btn-iniciar { -fx-background-color: linear-gradient(to bottom, #c77dff, #a64dff); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 9; }
            .btn-freno { -fx-background-color: #8b0000; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 9; }
            .db-selector { -fx-text-fill: white; }
            .left-scroll { -fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0; }
            .left-scroll > .viewport { -fx-background-color: transparent; }
            """;
        scene.getStylesheets().add("data:text/css," + style.replace("\n", ""));
    }

    private void conectarDB() {
        String host = txtHost.getText().trim();
        String portTxt = txtPort.getText().trim();
        String db = txtDb.getText().trim();
        String user = txtUser.getText().trim();
        String pass = txtPass.getText();

        if (host.isEmpty() || portTxt.isEmpty() || db.isEmpty() || user.isEmpty()) {
            errorMsg.setText("Completa host/puerto/bd/usuario/contraseña");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portTxt);
        } catch (NumberFormatException e) {
            errorMsg.setText("Puerto inválido");
            return;
        }

        try {
            DBComponentRegistry.clear(DatabaseType.POSTGRES);
            DBComponentConnector.ConnectResult result = connector.connect(
                    DatabaseType.POSTGRES, host, port, db, user, pass);
            DBComponentRegistry.put(result.type(), result.component());
            estadoPostgres.setText("PostgreSQL: conectado");
            estadoPostgres.setTextFill(Color.web("#7CFC00"));
            errorMsg.setText("Conectado correctamente a PostgreSQL");
            errorMsg.setTextFill(Color.web("#7CFC00"));
            btnSimular.setDisable(false);
        } catch (DBException e) {
            estadoPostgres.setText("PostgreSQL: error");
            estadoPostgres.setTextFill(Color.web("#ff4e8e"));
            errorMsg.setText("Error conectando: " + e.getMessage());
            errorMsg.setTextFill(Color.web("#ff4e8e"));
            btnSimular.setDisable(true);
        }
    }

    private void limpiarConexionActual() {
        DBComponentRegistry.clear(DatabaseType.POSTGRES);
        estadoPostgres.setText("PostgreSQL: desconectado");
        estadoPostgres.setTextFill(Color.web("#ff4e8e"));
        btnSimular.setDisable(true);
        errorMsg.setText("Conexión limpiada");
        errorMsg.setTextFill(Color.web("#b6aaff"));
    }

    private void ejecutarSimulacion() {
        if (!DBComponentRegistry.isConnected(DatabaseType.POSTGRES)) {
            errorMsg.setText("Primero conecta a PostgreSQL");
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
            errorMsg.setText("El número de peticiones debe estar entre 1 y 40000");
            return;
        }

        Platform.runLater(() -> {
            statsPool.setText("0% Éxito");
            targetProgresoPool = 0;
            panelGrafica.getChildren().clear();
        });

        new Thread(() -> {
            freno.desactivar();

            var colaCon = new java.util.concurrent.ConcurrentLinkedQueue<Resultado>();
            var contador = new ContadorEstadisticas();
            var hiloContador = new Thread(contador);
            hiloContador.start();

            var simulador = new Simulador(num, 1, () -> new DBQueryId("usuario.selectOne"), freno);

            final CountDownLatch terminado = new CountDownLatch(1);
            final ScheduledFuture<?>[] future = new ScheduledFuture<?>[1]; // Usamos array para que sea efectivamente final

            try {
                future[0] = scheduler.scheduleAtFixedRate(() -> {
                    int completadas = simulador.getCompletadas();
                    double progreso = completadas / (double) num;
                    Platform.runLater(() -> {
                        targetProgresoPool = Math.min(progreso, 1.0);
                        statsPool.setText("Pool: " + completadas + "/" + num + " | faltan " + (num - completadas));
                    });
                    if (completadas >= num || freno.estaActivado()) {
                        if (future[0] != null) future[0].cancel(false);
                        terminado.countDown();
                    }
                }, 0, 20, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }

            simulador.ejecutarConPool(contador, progreso -> {});

            contador.detener();
            try { hiloContador.join(); } catch (InterruptedException ignored) {}

            try { terminado.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

            Platform.runLater(() -> {
                int ex = contador.getExitosas();
                int fa = contador.getFallidas();
                double pct = contador.getPorcentajeExito();
                statsPool.setText(String.format("Pool: %d ok / %d fail | %.2f%% éxito", ex, fa, pct));
                mostrarGrafica(ex, fa);
            });
        }).start();
    }

    private void mostrarGrafica(int exitosas, int fallidas) {
        panelGrafica.getChildren().clear();
        GraficoTorta grafica = new GraficoTorta("Resultados Pool");
        grafica.actualizar(exitosas, fallidas);
        panelGrafica.getChildren().add(grafica);
    }
}