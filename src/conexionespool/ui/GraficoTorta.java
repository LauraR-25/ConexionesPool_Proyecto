package conexionespool.ui;

import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class GraficoTorta extends StackPane {
    private final PieChart pieChart;
    private final Label lblPorcentaje;

    public GraficoTorta(String titulo) {
        pieChart = new PieChart();
        pieChart.setTitle(titulo);
        pieChart.setLegendVisible(false);
        pieChart.setAnimated(false);
        pieChart.setStyle("-fx-background-color: transparent;");
        pieChart.setPrefSize(350, 350); // Más grande

        lblPorcentaje = new Label("0%");
        lblPorcentaje.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        lblPorcentaje.setTextFill(Color.web("#ffb3d9"));
        lblPorcentaje.setStyle("-fx-effect: dropshadow(gaussian, #c77dff, 5, 0.2, 0, 0);");

        // Añadir un segmento dummy para que el gráfico se vea incluso sin datos
        PieChart.Data dummy = new PieChart.Data("", 1);
        pieChart.getData().add(dummy);
        dummy.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: #3a2a4a;"); // Color gris oscuro
        });

        this.getChildren().addAll(pieChart, lblPorcentaje);
        this.setAlignment(Pos.CENTER);
    }

    public void actualizar(int exitosas, int fallidas) {
        pieChart.getData().clear();
        int total = exitosas + fallidas;
        if (total == 0) {
            // Si no hay datos, mostrar un segmento gris
            PieChart.Data dummy = new PieChart.Data("", 1);
            pieChart.getData().add(dummy);
            dummy.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) newNode.setStyle("-fx-pie-color: #3a2a4a;");
            });
            lblPorcentaje.setText("0%");
            return;
        }
        double pctExito = (exitosas * 100.0) / total;

        PieChart.Data sliceExito = new PieChart.Data("Éxito", exitosas);
        PieChart.Data sliceFallo = new PieChart.Data("Fallo", fallidas);
        pieChart.getData().addAll(sliceExito, sliceFallo);

        // Colores personalizados
        sliceExito.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: #4CAF50;");
        });
        sliceFallo.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: #F44336;");
        });

        lblPorcentaje.setText(String.format("%.1f%%", pctExito));
    }

    public void limpiar() {
        // Restablecer al estado inicial con un segmento gris
        pieChart.getData().clear();
        PieChart.Data dummy = new PieChart.Data("", 1);
        pieChart.getData().add(dummy);
        dummy.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: #3a2a4a;");
        });
        lblPorcentaje.setText("0%");
    }
}