package conexionesPool;

public class main {
    public static void main(String[] args) {
        try {
            // Cargar configuración
            ConfiguracionEntorno config = new ConfiguracionEntorno(".env");
            String url = "jdbc:postgresql://" + config.obtener("DB_HOST") + ":"
                    + config.obtener("DB_PORT") + "/" + config.obtener("DB_NAME");
            String user = config.obtener("DB_USER");
            String pass = config.obtener("DB_PASSWORD");
            int tamanoPool = config.obtenerEntero("POOL_SIZE");
            long timeout = config.obtenerLargo("POOL_TIMEOUT");

            // Cargar driver (opcional con JDBC 4+)
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                Impresion.println("Driver PostgreSQL no encontrado. Verifica lib/");
                return;
            }

            // Crear pool y administrador
            PoolConexiones pool = new PoolConexiones(url, user, pass, tamanoPool, timeout);
            AdministradorPool admin = new AdministradorPool(pool);

            // Simulación
            int peticiones = 15000; // Puedes cambiarlo
            SimuladorPeticiones sim = new SimuladorPeticiones(peticiones);

            Impresion.println("=== Simulación SIN pool ===");
            sim.ejecutarSinPool(url, user, pass);

            Impresion.println("\n=== Simulación CON pool (versión propia) ===");
            sim.ejecutarConPool(admin);

            // Cerrar pool
            admin.cerrarPool();
        } catch (Exception e) {
            System.err.println("Error fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}