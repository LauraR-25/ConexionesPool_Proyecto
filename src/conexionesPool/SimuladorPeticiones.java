package conexionesPool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimuladorPeticiones {
    private final int numeroPeticiones;
    private final AtomicBoolean freno = new AtomicBoolean(false);

    public SimuladorPeticiones(int numeroPeticiones) {
        this.numeroPeticiones = numeroPeticiones;
    }

    private void activarFreno() {
        freno.set(true);
    }

    private boolean estaFrenado() {
        return freno.get();
    }

    private Thread iniciarEscuchaFreno() {
        Thread t = new Thread(() -> {
            try {
                while (!estaFrenado()) {
                    if (System.in.available() > 0) {
                        int input = System.in.read();
                        if (input == '\n' || input == '\r') {
                            Impresion.println("\n[FRENO] Activado por usuario. Deteniendo...");
                            activarFreno();
                            break;
                        }
                    }
                    Thread.sleep(50);
                }
            } catch (Exception ignored) {}
        }, "EscuchaFreno");
        t.setDaemon(true);
        t.start();
        return t;
    }

    public void ejecutarSinPool(String url, String usuario, String password) {
        freno.set(false);
        Thread hiloFreno = iniciarEscuchaFreno();
        long inicio = System.currentTimeMillis();
        Thread[] hilos = new Thread[numeroPeticiones];

        for (int i = 0; i < numeroPeticiones; i++) {
            final int idx = i + 1;
            hilos[i] = new Thread(() -> {
                if (estaFrenado()) return;
                try (Connection conn = DriverManager.getConnection(url, usuario, password)) {
                    if (estaFrenado()) return;
                    try (var stmt = conn.createStatement()) {
                        var rs = stmt.executeQuery("SELECT * FROM usuario LIMIT 1");
                        while (rs.next() && !estaFrenado()) {
                            Impresion.println("[SIN POOL] Petición " + idx + ": usuario = " + rs.getString(1));
                        }
                    }
                    Thread.sleep(new Random().nextInt(500));
                } catch (Exception e) {
                    Impresion.println("[SIN POOL] Error petición " + idx + ": " + e.getMessage());
                }
            });
            hilos[i].start();
        }

        esperarHilos(hilos);
        freno.set(true);
        esperarHiloFreno(hiloFreno);
        long fin = System.currentTimeMillis();
        Impresion.println("Tiempo total SIN pool: " + (fin - inicio) + " ms");
    }

    public void ejecutarConPool(AdministradorPool admin) {
        freno.set(false);
        Thread hiloFreno = iniciarEscuchaFreno();
        long inicio = System.currentTimeMillis();
        Thread[] hilos = new Thread[numeroPeticiones];

        for (int i = 0; i < numeroPeticiones; i++) {
            final int idx = i + 1;
            hilos[i] = new Thread(() -> {
                if (estaFrenado()) return;
                Connection conn = null;
                try {
                    conn = admin.tomarConexion();
                    if (estaFrenado()) return;
                    try (var stmt = conn.createStatement()) {
                        var rs = stmt.executeQuery("SELECT * FROM usuario LIMIT 1");
                        while (rs.next() && !estaFrenado()) {
                            Impresion.println("[CON POOL] Petición " + idx + ": usuario = " + rs.getString(1));
                        }
                    }
                    Thread.sleep(new Random().nextInt(500));
                } catch (SQLException e) {
                    Impresion.println("[CON POOL] Error SQL petición " + idx + ": " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Impresion.println("[CON POOL] Petición " + idx + " interrumpida");
                } finally {
                    if (conn != null) admin.devolverConexion(conn);
                }
            });
            hilos[i].start();
        }

        esperarHilos(hilos);
        freno.set(true);
        esperarHiloFreno(hiloFreno);
        long fin = System.currentTimeMillis();
        Impresion.println("Tiempo total CON pool: " + (fin - inicio) + " ms");
        admin.mostrarEstadisticas();
    }

    private void esperarHilos(Thread[] hilos) {
        for (Thread h : hilos) {
            try { h.join(); } catch (InterruptedException ignored) {}
        }
    }

    private void esperarHiloFreno(Thread h) {
        try { if (h != null) h.join(100); } catch (InterruptedException ignored) {}
    }
}