package conexionespool.util;

public final class DriverCheck {
    private DriverCheck() {}

    public static boolean isDriverPresent(String driverClassName) {
        if (driverClassName == null || driverClassName.isBlank()) return false;
        try {
            Class.forName(driverClassName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

