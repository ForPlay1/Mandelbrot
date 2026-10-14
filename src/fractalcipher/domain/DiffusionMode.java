package fractalcipher.domain;

public enum DiffusionMode {
    LOGISTIC("Логистический"),
    HENON("Хенон"),
    TENT("Тент"),
    LORENZ("Лоренц"),
    ROSSLER("Рёсслер"),
    ARNOLD("Арнольд"),
    NULL("Нет");

    private final String translation;

    DiffusionMode(String translation) {
        this.translation = translation;
    }

    public String getTranslation() {
        return translation;
    }
}
