package fractalcipher.domain;

public enum DiffusionMode {
    LOGISTIC("logistic"),
    HENON("henon"),
    TENT("tent"),
    LORENZ("lorenz"),
    ROSSLER("rossler"),
    ARNOLD("arnold"),
    NULL("null");

    private final String name;

    DiffusionMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
