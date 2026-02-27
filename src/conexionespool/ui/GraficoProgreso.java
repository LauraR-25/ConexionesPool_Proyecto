package conexionespool.ui;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class GraficoProgreso extends VBox {
    private final LineChart<Number, Number> lineChart;
    private final XYChart.Series<Number, Number> serie;
    private int contadorPuntos = 0;

    public GraficoProgreso(String titulo) {
        NumberAxis ejeX = new NumberAxis();
        ejeX.setLabel("Progreso (%)");
        ejeX.setTickLabelFill(Color.web("#d4b5ff"));

        NumberAxis ejeY = new NumberAxis();
        ejeY.setLabel("Completado");
        ejeY.setTickLabelFill(Color.web("#d4b5ff"));

        lineChart = new LineChart<>(ejeX, ejeY);
        lineChart.setTitle(titulo);
        lineChart.setLegendVisible(false);
        lineChart.setAnimated(false);
        lineChart.setPrefSize(400, 220); // Más grande
        lineChart.setStyle("-fx-background-color: transparent;");

        serie = new XYChart.Series<>();
        // Cambiar color de la línea a rosa pastel (#ffb3d9)
        serie.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: #ffb3d9; -fx-stroke-width: 2;");
            }
        });
        lineChart.getData().add(serie);

        this.getChildren().add(lineChart);
        this.setStyle("-fx-padding: 10;");
    }

    public void agregarPunto(double progreso) {
        contadorPuntos++;
        serie.getData().add(new XYChart.Data<>(contadorPuntos, progreso * 100));
    }

    public void limpiar() {
        serie.getData().clear();
        contadorPuntos = 0;
    }
}