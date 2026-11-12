package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;

/**
 * Диффузия на основе логистической карты.
 * <p>
 * Формула: {@code x_{n+1} = r · x_n · (1 − x_n)}.
 * </p>
 * <p>
 * При {@code r ≈ 3.99} карта находится в хаотическом режиме: малые
 * изменения {@code x0} приводят к совершенно разным траекториям, что
 * даёт хороший лавинный эффект. Значение по умолчанию {@code r = 3.99}
 * выбрано специально — в окрестности 4.0 карта в {@code double} может
 * вырождаться из-за округления.
 * </p>
 * <p>
 * Структура: два прохода со сцеплением, опциональный S-бокс. Это
 * простейшая из всех реализаций — используется как эталон при
 * сравнении в бенчмарке.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 */
public class LogisticMapDiffusion implements DiffusionSource {

    /**
     * {@inheritDoc}
     * <p>
     * Ожидаемые параметры:
     * </p>
     * <ul>
     *   <li>{@code "r"} — параметр карты (по умолчанию {@code 3.99});</li>
     *   <li>{@code "x0"} — начальное значение в {@code (0; 1)};</li>
     *   <li>{@code "sboxSeed"} — seed S-бокса (0 — отключён).</li>
     * </ul>
     */
    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        final double r = params.getOrDefault("r", 3.99);
        final long seed = params.getOrDefault("seed", 0.0).longValue();
        final Random rnd = new Random(seed);

        // x0 — часть ключа. Если не передан — генерируем случайно
        // (но в реальном шифровании x0 ВСЕГДА должен приходить из ключа).
        final double x0 = params.getOrDefault("x0", DiffusionUtils.randomCord(rnd));

        // sboxSeed — опциональный (если 0, S-бокс не применяется)
        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        final long keyBits = DiffusionUtils.keyBits(x0, r);

        // Траектория считается блоками параллельно: блок №0 идёт с x0,
        // остальные — со своих ключезависимых стартовых точек.
        final byte[] stream = DiffusionUtils.chainTwoPassParallel(length, chunk -> {
            final double[] x = {chunk == 0
                    ? x0
                    : DiffusionUtils.randomCord(DiffusionUtils.chunkRandom(keyBits, chunk))};
            return () -> {
                x[0] = r * x[0] * (1 - x[0]);
                return ((int) (x[0] * 256)) & 0xFF;
            };
        });

        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    /** @return строковое имя источника — {@code "logistic"} */
    @Override
    public String getName() {
        return "logistic";
    }
}