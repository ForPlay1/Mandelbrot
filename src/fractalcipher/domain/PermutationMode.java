package fractalcipher.domain;

public enum PermutationMode {
    MANDELBROT("Мандельброт"),
    JULIA("Жюлиа"),
    CANTOR("Кантор"),
    BURNING_SHIP("Горящий корабль");

    private final String name;

    PermutationMode(String translation) {
        this.name = translation;
    }

    public String getName() {
        return name;
    }
}
