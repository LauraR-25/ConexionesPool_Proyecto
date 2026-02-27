package conexionespool.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class Freno {
    private final AtomicBoolean activo = new AtomicBoolean(false);

    public void activar() {
        activo.set(true);
    }

    public void desactivar() {
        activo.set(false);
    }

    public boolean estaActivado() {
        return activo.get();
    }
}