package conexionespool.ui;

import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;

public class GraficoBarras extends VBox {
    private final BarChart<String, Number> barChart;
    private final CategoryAxis ejeX;
    private final NumberAxis ejeY;

    public GraficoBarras() {
        ejeX = new CategoryAxis();
        ejeY = new NumberAxis();
        ejeX.setLabel("Tipo");
        ejeY.setLabel("Cantidad");
        barChart = new BarChart<>(ejeX, ejeY);
        barChart.setTitle("Resultados de la simulación");
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(300);
        barChart.setStyle("-fx-background-color: #2b2b3a;");
        this.getChildren().add(barChart);
        this.setStyle("-fx-padding: 15; -fx-alignment: center; -fx-background-color: #1e1e2f;");
    }

    public void mostrarResultados(int exitosasSin, int fallidasSin, int exitosasCon, int fallidasCon) {
        barChart.getData().clear(); // Limpiar datos anteriores

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.getData().add(new XYChart.Data<>("Sin pool - Éxito", exitosasSin));
        serie.getData().add(new XYChart.Data<>("Sin pool - Fracaso", fallidasSin));
        serie.getData().add(new XYChart.Data<>("Con pool - Éxito", exitosasCon));
        serie.getData().add(new XYChart.Data<>("Con pool - Fracaso", fallidasCon));

        // Colores personalizados (opcional)
        serie.getData().forEach(d -> d.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                String color = switch (d.getXValue()) {
                    case "Sin pool - Éxito" -> "#6ecf7e";
                    case "Sin pool - Fracaso" -> "#e06c6c";
                    case "Con pool - Éxito" -> "#9b8df0";
                    case "Con pool - Fracaso" -> "#f0b27a";
                    default -> "#aaaaaa";
                };
                newNode.setStyle("-fx-bar-fill: " + color + ";");
            }
        }));

        barChart.getData().add(serie);
    }
}