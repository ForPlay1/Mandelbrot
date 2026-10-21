package fractalcipher.domain;

public enum PermutationMode {
    MANDELBROT("Мандельброт"),
    JULIA("Жюлиа"),
    CANTOR("Кантор"),
    BURNING_SHIP("Горящий корабль"),
    SIERPINSKI("Серпинский"), // <-- НОВОЕ
    DRAGON("Дракон");         // <-- НОВОЕ


    private final String name;

    PermutationMode(String translation) {
        this.name = translation;
    }

    public String getName() {
        return name;
    }
}
