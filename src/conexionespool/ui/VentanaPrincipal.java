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

    // Progresso suavizado
    private double targetProgresoRaw = 0, shownProgresoRaw = 0;
    private double targetProgresoPool = 0, shownProgresoPool = 0;
    private AnimationTimer smoothTimer;

    private final Label errorMsg = new Label("");

    @Override
    public void start(Stage stage) {
        ConfiguracionEntorno config = new ConfiguracionEntorno(".env");
        String host = config.obtener("DB_HOST");
        String port = config.obtener("DB_PORT");
        String db = config.obtener("DB_NAME");
        String user = config.obtener("DB_USER");
        String pass = config.obtener("DB_PASSWORD");

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

        // Prellenar campos y conectar automáticamente
        Platform.runLater(() -> conectarDB(host, port, db, user, pass));

        stage.setTitle("ConexionesPool - Simulación (Raw vs Pool)");
        stage.setScene(scene);
        stage.show();

        startSmoothProgressAnimation();

        btnSimular.setOnAction(_ -> ejecutarSimulacion());
    }

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

    private void conectarDB(String host, String portTxt, String db, String user, String pass) {
        if (host.isEmpty() || portTxt.isEmpty() || db.isEmpty() || user.isEmpty()) {
            errorMsg.setText("Completa todos los campos");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portTxt);
        } catch (NumberFormatException e) {
            errorMsg.setText("Puerto inválido");
            return;
        }

        DatabaseType tipo = cmbDatabase.getValue();
        try {
            DBComponentRegistry.clear(tipo);
            DBComponentConnector.ConnectResult result = connector.connect(
                    tipo, host, port, db, user, pass);
            DBComponentRegistry.put(result.type(), result.component());
            estadoConexion.setText(tipo + ": conectado");
            estadoConexion.setTextFill(Color.web("#7CFC00"));
            errorMsg.setText("Conectado correctamente a " + tipo);
            errorMsg.setTextFill(Color.web("#7CFC00"));
            btnSimular.setDisable(false);

            // --- Inicialización de tablas para H2 y MySQL con depuración ---
            if (tipo == DatabaseType.H2) {
                try {
                    String h2Url = "jdbc:h2:./databases/" + db;
                    System.out.println("DEBUG H2: Intentando conectar con URL: " + h2Url);
                    Connection conn = DriverManager.getConnection(h2Url, user, pass);
                    System.out.println("DEBUG H2: Conexión exitosa.");
                    Statement st = conn.createStatement();
                    st.execute("CREATE TABLE IF NOT EXISTS usuario (id INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100))");
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuario");
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        st.execute("INSERT INTO usuario (nombre) VALUES ('Prueba')");
                        System.out.println("DEBUG H2: Se insertó registro de prueba.");
                    } else {
                        System.out.println("DEBUG H2: La tabla ya tenía datos.");
                    }
                    rs.close();
                    st.close();
                    conn.close();
                    System.out.println("DEBUG H2: Tabla usuario verificada/creada.");
                } catch (Exception e) {
                    System.err.println("DEBUG H2: ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (tipo == DatabaseType.MYSQL) {
                try {
                    String mysqlUrl = "jdbc:mysql://" + host + ":" + port + "/" + db;
                    System.out.println("DEBUG MySQL: Intentando conectar con URL: " + mysqlUrl);
                    Connection conn = DriverManager.getConnection(mysqlUrl, user, pass);
                    System.out.println("DEBUG MySQL: Conexión exitosa.");
                    Statement st = conn.createStatement();
                    st.execute("CREATE TABLE IF NOT EXISTS usuario (id INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100))");
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuario");
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        st.execute("INSERT INTO usuario (nombre) VALUES ('Prueba')");
                        System.out.println("DEBUG MySQL: Se insertó registro de prueba.");
                    } else {
                        System.out.println("DEBUG MySQL: La tabla ya tenía datos.");
                    }
                    rs.close();
                    st.close();
                    conn.close();
                    System.out.println("DEBUG MySQL: Tabla usuario verificada/creada.");
                } catch (Exception e) {
                    System.err.println("DEBUG MySQL: ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            }

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
        estadoConexion.setText(tipo + ": desconectado");
        estadoConexion.setTextFill(Color.web("#ff4e8e"));
        btnSimular.setDisable(true);
        errorMsg.setText("Conexión limpiada");
        errorMsg.setTextFill(Color.web("#b6aaff"));
    }

    private void ejecutarSimulacion() {
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

        // Reset UI
        Platform.runLater(() -> {
            targetProgresoRaw = 0;
            targetProgresoPool = 0;
            lblEstadoRaw.setText("Raw: en progreso...");
            lblEstadoPool.setText("Pool: en progreso...");
            graficaRaw.limpiar();
            graficaPool.limpiar();
            lblResumen.setText("");
        });

        new Thread(() -> {
            freno.desactivar();

            // Construir URL para la simulación Raw según el tipo de base de datos
            String url;
            if (tipo == DatabaseType.POSTGRES) {
                url = "jdbc:postgresql://" + txtHost.getText().trim() + ":" + txtPort.getText().trim() + "/" + txtDb.getText().trim();
            } else if (tipo == DatabaseType.MYSQL) {
                url = "jdbc:mysql://" + txtHost.getText().trim() + ":" + txtPort.getText().trim() + "/" + txtDb.getText().trim();
            } else if (tipo == DatabaseType.H2) {
                url = "jdbc:h2:./databases/" + txtDb.getText().trim();
            } else {
                url = "jdbc:postgresql://" + txtHost.getText().trim() + ":" + txtPort.getText().trim() + "/" + txtDb.getText().trim();
            }

            String user = txtUser.getText().trim();
            String pass = txtPass.getText();
            String query = "SELECT * FROM usuario LIMIT 1";

            // Contadores y simuladores
            ContadorEstadisticas contadorRaw = new ContadorEstadisticas();
            ContadorEstadisticas contadorPool = new ContadorEstadisticas();

            Thread hiloContadorRaw = new Thread(contadorRaw);
            Thread hiloContadorPool = new Thread(contadorPool);
            hiloContadorRaw.start();
            hiloContadorPool.start();

            SimuladorRaw simuladorRaw = new SimuladorRaw(num, 1, () -> query, freno, url, user, pass);
            Simulador simuladorPool = new Simulador(num, 1, () -> new DBQueryId("usuario.selectOne"), freno);

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

            Thread hiloRaw = new Thread(() -> simuladorRaw.ejecutar(contadorRaw, p -> {}));
            Thread hiloPool = new Thread(() -> simuladorPool.ejecutarConPool(contadorPool, p -> {}));
            hiloRaw.start();
            hiloPool.start();

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
        }).start();
    }
}