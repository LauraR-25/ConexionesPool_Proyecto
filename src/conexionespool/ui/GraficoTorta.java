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
        pieChart.setPrefSize(300, 300);

        lblPorcentaje = new Label("0%");
        lblPorcentaje.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        lblPorcentaje.setTextFill(Color.web("#ffb3d9"));
        lblPorcentaje.setStyle("-fx-effect: dropshadow(gaussian, #c77dff, 5, 0.2, 0, 0);");

        this.getChildren().addAll(pieChart, lblPorcentaje);
        this.setAlignment(Pos.CENTER);
    }

    public void actualizar(int exitosas, int fallidas) {
        pieChart.getData().clear();
        int total = exitosas + fallidas;

        if (total == 0) {
            // Si no hay datos, mostrar un segmento gris (como estaba antes)
            PieChart.Data dummy = new PieChart.Data("", 1);
            pieChart.getData().add(dummy);
            dummy.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) newNode.setStyle("-fx-pie-color: #3a2a4a;");
            });
            lblPorcentaje.setText("0%");
            return;
        }

        // --- CORRECCIÓN: Crear SIEMPRE ambas porciones ---
        PieChart.Data sliceExito = new PieChart.Data("Éxito", exitosas);
        PieChart.Data sliceFallo = new PieChart.Data("Fallo", fallidas);
        pieChart.getData().addAll(sliceExito, sliceFallo);

        // Calcular y mostrar el porcentaje de ÉXITO en el centro
        double pctExito = (exitosas * 100.0) / total;
        lblPorcentaje.setText(String.format("%.1f%%", pctExito));

        // Asignar colores ALTAMENTE CONTRASTANTES para que se vean bien
        sliceExito.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: #4CAF50;"); // Verde vibrante
        });
        sliceFallo.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: #F44336;"); // Rojo vibrante
        });
    }

    public void limpiar() {
        pieChart.getData().clear();
        // Estado inicial con segmento gris
        PieChart.Data dummy = new PieChart.Data("", 1);
        pieChart.getData().add(dummy);
        dummy.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.setStyle("-fx-pie-color: #3a2a4a;");
        });
        lblPorcentaje.setText("0%");
    }
}