package fractalcipher.domain;

public enum DiffusionMode {
    LOGISTIC("Логистический"),
    HENON("Хенон"),
    TENT("Тент"),
    LORENZ("Лоренц"),
    ROSSLER("Рёсслер"),
    ARNOLD("Арнольд"),
    NULL("Нет");

    private final String name;

    DiffusionMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
