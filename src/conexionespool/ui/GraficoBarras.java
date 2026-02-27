package conexionespool.ui;

import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class GraficoBarras extends VBox {
    private final BarChart<String, Number> barChart;

    public GraficoBarras() {
        CategoryAxis ejeX = new CategoryAxis();
        NumberAxis ejeY = new NumberAxis();
        barChart = new BarChart<>(ejeX, ejeY);
        barChart.setTitle("Resultados de la simulación");
        barChart.setLegendVisible(false);
        barChart.setAnimated(false);
        barChart.setStyle("-fx-background-color: transparent;");
        barChart.setPrefHeight(350);

        ejeX.setTickLabelFill(Color.web("#a88ff0"));
        ejeY.setTickLabelFill(Color.web("#a88ff0"));

        this.getChildren().add(barChart);
        this.setAlignment(Pos.CENTER);
    }

    public void mostrarResultados(int exitosasSin, int fallidasSin, int exitosasCon, int fallidasCon) {
        barChart.getData().clear();
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.getData().add(new XYChart.Data<>("Sin pool ✓", exitosasSin));
        serie.getData().add(new XYChart.Data<>("Sin pool ✗", fallidasSin));
        serie.getData().add(new XYChart.Data<>("Con pool ✓", exitosasCon));
        serie.getData().add(new XYChart.Data<>("Con pool ✗", fallidasCon));
        barChart.getData().add(serie);

        serie.getData().forEach(d -> d.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                String color = switch (d.getXValue()) {
                    case "Sin pool ✓" -> "#4CAF50";
                    case "Sin pool ✗" -> "#F44336";
                    case "Con pool ✓" -> "#8BC34A";
                    case "Con pool ✗" -> "#FF9800";
                    default -> "#2196F3";
                };
                newNode.setStyle("-fx-bar-fill: " + color + ";");
            }
        }));
    }
}