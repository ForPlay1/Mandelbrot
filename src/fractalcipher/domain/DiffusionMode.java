package fractalcipher.domain;

/**
 * Источник диффузии — хаотическая карта или система ОДУ,
 * генерирующая псевдослучайный поток байт для XOR с пикселями.
 * <p>
 * Строковое имя в конструкторе ({@link #getName()}) должно совпадать
 * с результатом {@code getName()} соответствующей реализации
 * {@link fractalcipher.model.diffusion.DiffusionSource}, иначе
 * {@link fractalcipher.model.diffusion.DiffusionSourceFactory}
 * выбросит {@link fractalcipher.exception.UnsupportedSourceException}.
 * </p>
 *
 * @author FractalCipher Team
 * @see fractalcipher.model.diffusion.DiffusionSourceFactory
 */
public enum DiffusionMode {
    /** Логистическая карта {@code x_{n+1} = r·x_n·(1−x_n)}. */
    LOGISTIC("logistic"),

    /** Карта Хенона {@code x_{n+1} = 1 − a·x_n² + y_n, y_{n+1} = b·x_n}. */
    HENON("henon"),

    /** Тент-карта (кусочно-линейная). */
    TENT("tent"),

    /** Система Лоренца (аттрактор Лоренца, интегрирование RK4). */
    LORENZ("lorenz"),

    /** Система Рёсслера (аттрактор Рёсслера, интегрирование RK4). */
    ROSSLER("rossler"),

    /** Карта Арнольда (кошачья карта на торе). */
    ARNOLD("arnold"),

    /** Заглушка: нулевой поток (данные не меняются). */
    NULL("null");

    /** Строковое имя, используемое фабрикой источников диффузии. */
    private final String name;

    DiffusionMode(String name) {
        this.name = name;
    }

    /**
     * @return строковое имя источника (например, {@code "logistic"})
     */
    public String getName() {
        return name;
    }
}
