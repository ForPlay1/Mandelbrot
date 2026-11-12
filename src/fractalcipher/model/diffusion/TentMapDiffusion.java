package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;
import fractalcipher.exception.InvalidConfigurationException;

/**
 * Диффузия на основе тент-карты (кусочно-линейной).
 * <p>
 * Формула:
 * </p>
 * <pre>
 *   x_{n+1} = mu · x_n            , если x_n &lt; 0.5
 *   x_{n+1} = mu · (1 − x_n)      , если x_n ≥ 0.5
 * </pre>
 * <p>
 * Параметр {@code mu} должен лежать в {@code (1; 2]}. При {@code mu = 2}
 * карта в {@code double} вырождается: умножение на 2 — это просто сдвиг
 * мантиссы, и через ~53 шага {@code x} обнуляется. Поэтому по умолчанию
 * берём {@code mu} чуть меньше 2 (в {@code [1; 2]} с небольшим запасом
 * от верхней границы).
 * </p>
 * <p>
 * Структура та же, что у {@link LogisticMapDiffusion} и
 * {@link HenonMapDiffusion}: прогрев, два прохода со сцеплением,
 * опциональный S-бокс.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 * @see LogisticMapDiffusion
 */
public class TentMapDiffusion implements DiffusionSource {

    /** Сколько первых итераций отбрасываем, чтобы уйти от начальной точки. */
    private static final int BURN_IN = 1000;

    /**
     * {@inheritDoc}
     * <p>
     * Ожидаемые параметры:
     * </p>
     * <ul>
     *   <li>{@code "mu"} — параметр карты в {@code [1; 2]};</li>
     *   <li>{@code "x0"} — начальное значение в {@code [0; 1]};</li>
     *   <li>{@code "sboxSeed"} — seed S-бокса (0 — отключён).</li>
     * </ul>
     *
     * @throws InvalidConfigurationException если {@code mu} вне
     *         {@code [1; 2]} или {@code x0} вне {@code [0; 1]}
     */
    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        final long seed = params.getOrDefault("seed", 0.0).longValue();
        final Random rnd = new Random(seed);
        final double mu = params.getOrDefault("mu", DiffusionUtils.randomCord(rnd) + 1.0);
        if (!(mu >= 1.0 && mu <= 2.0)) {
            throw new InvalidConfigurationException(
                    "Параметр mu тент-карты должен быть в интервале (1; 2), получено: " + mu);
        }

        // x0 — часть ключа. Если не передан — случайный
        // (в реальном шифровании x0 ВСЕГДА должен приходить из ключа).
        final double x0 = params.getOrDefault("x0", DiffusionUtils.randomCord(rnd));
        if (!(x0 >= 0.0 && x0 <= 1.0)) {
            throw new InvalidConfigurationException(
                    "Начальное значение x0 тент-карты должно быть в интервале (0; 1), получено: " + x0);
        }

        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Прогрев (для блока №0)
        final double burned = burnIn(x0, mu);
        final long keyBits = DiffusionUtils.keyBits(x0, mu);

        // Траектория считается блоками параллельно: блок №0 идёт с прогретой x0,
        // остальные — с собственных ключезависимых стартовых точек (тоже с прогревом).
        final byte[] stream = DiffusionUtils.chainTwoPassParallel(length, chunk -> {
            final double[] x = {chunk == 0
                    ? burned
                    : burnIn(DiffusionUtils.randomCord(DiffusionUtils.chunkRandom(keyBits, chunk)), mu)};
            return () -> {
                x[0] = step(x[0], mu);
                return DiffusionUtils.fracByte(x[0]);
            };
        });

        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    /**
     * Выполняет {@link #BURN_IN} шагов карты от точки {@code start}.
     *
     * @param start начальное значение
     * @param mu    параметр карты
     * @return состояние после прогрева
     */
    private static double burnIn(final double start, final double mu) {
        double x = start;
        for (int i = 0; i < BURN_IN; i++) {
            x = step(x, mu);
        }
        return x;
    }

    /**
     * Один шаг тент-карты.
     * <p>
     * Защита от вырождения в 0: если из-за ошибок округления значение
     * стало ≤ {@code 1e-12}, детерминированно подставляем константу,
     * чтобы поток не «залип» в нуле.
     * </p>
     *
     * @param x  текущее значение
     * @param mu параметр карты
     * @return новое значение
     */
    private static double step(final double x, final double mu) {
        final double nx = (x < 0.5) ? mu * x : mu * (1.0 - x);
        // Защита от вырождения в 0 (фиксированная точка) из-за ошибок округления:
        // детерминированно подмешиваем малую константу, чтобы поток не залип.
        return (nx <= 1e-12) ? 0.123456789 : nx;
    }

    /** @return строковое имя источника — {@code "tent"} */
    @Override
    public String getName() {
        return "tent";
    }
}