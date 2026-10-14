package fractalcipher.domain;

public enum PermutationMode {
    MANDELBROT("Мандельброт"),
    JULIA("Жюлиа"),
    CANTOR("Кантор"),
    BURNING_SHIP("Горящий корабль");

    private final String translation;

    PermutationMode(String translation) {
        this.translation = translation;
    }

    public String getTranslation() {
        return translation;
    }
}
