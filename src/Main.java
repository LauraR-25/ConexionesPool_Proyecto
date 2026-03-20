package conexionespool;

import javafx.application.Application;
import conexionespool.ui.VentanaPrincipal;

public class Main {
    static {
        try {
            Class.forName("org.postgresql.Driver");
            System.out.println("✅ Driver PostgreSQL cargado correctamente.");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Error: No se encontró el driver PostgreSQL.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Application.launch(VentanaPrincipal.class, args);
    }
}