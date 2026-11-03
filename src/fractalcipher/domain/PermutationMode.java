package fractalcipher.domain;

public enum PermutationMode {
    MANDELBROT("mandelbrot"),
    JULIA("julia"),
    CANTOR("cantor"),
    BURNING_SHIP("burning ship"),
    SIERPINSKI("sierphinski"), // <-- НОВОЕ
    DRAGON("dragon");         // <-- НОВОЕ


    private final String name;

    PermutationMode(String translation) {
        this.name = translation;
    }

    public String getName() {
        return name;
    }
}
