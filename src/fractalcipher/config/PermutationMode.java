package fractalcipher.config;

/**
 * Источник перестановки пикселей (confusion-часть шифра).
 * <p>
 * Строковое имя в конструкторе ({@link #getName()}) должно совпадать
 * с результатом {@code getName()} соответствующей реализации
 * {@link fractalcipher.model.permutation.PermutationSource}, иначе
 * {@link fractalcipher.model.permutation.PermutationSourceFactory}
 * выбросит {@link fractalcipher.exception.UnsupportedSourceException}.
 * </p>
 * <p>
 * <b>Внимание:</b> в имени {@link #SIERPINSKI} сохранена оригинальная
 * опечатка {@code "sierphinski"} — она совпадает с именем, которое
 * возвращает {@code SierpinskiPermutation.getName()}. Менять её в одном
 * месте без второго нельзя: нарушится связь «enum → фабрика».
 * </p>
 *
 * @author FractalCipher Team
 * @see fractalcipher.model.permutation.PermutationSourceFactory
 */
public enum PermutationMode {
    /** Множество Мандельброта. */
    MANDELBROT("mandelbrot"),

    /** Множество Жюлиа (с фиксированным {@code c} из ключа). */
    JULIA("julia"),

    /** Канторова диагональная матрица. */
    CANTOR("cantor"),

    /** Фрактал «Горящий корабль» (Burning Ship). */
    BURNING_SHIP("burning ship"),

    /** Треугольник Серпинского. */
    SIERPINSKI("sierpinski"),

    /** Кривая Дракона (Dragon Curve). */
    DRAGON("dragon");

    /** Строковое имя, используемое фабрикой источников перестановки. */
    private final String name;

    /**
     * @param sourceName идентификатор источника для
     *                   {@link fractalcipher.model.permutation.PermutationSourceFactory}
     *                   (например, {@code "mandelbrot"})
     */
    PermutationMode(String sourceName) {
        this.name = sourceName;
    }

    /**
     * @return строковое имя источника (например, {@code "mandelbrot"})
     */
    public String getName() {
        return name;
    }
}
