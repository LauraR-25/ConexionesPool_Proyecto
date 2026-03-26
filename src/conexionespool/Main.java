package conexionespool;

import javafx.application.Application;
import conexionespool.ui.VentanaPrincipal;
import conexionespool.util.DriverCheck;

public class Main {
    static {
        System.out.println("Drivers disponibles:");
        System.out.println("- Postgres (org.postgresql.Driver): " + (DriverCheck.isDriverPresent("org.postgresql.Driver") ? "OK" : "FALTA"));
        System.out.println("- H2 (org.h2.Driver): " + (DriverCheck.isDriverPresent("org.h2.Driver") ? "OK" : "FALTA"));
        System.out.println("- MySQL (com.mysql.cj.jdbc.Driver): " + (DriverCheck.isDriverPresent("com.mysql.cj.jdbc.Driver") ? "OK" : "FALTA"));
    }

    public static void main(String[] args) {
        Application.launch(VentanaPrincipal.class, args);
    }
}

