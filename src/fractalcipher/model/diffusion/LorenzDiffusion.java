package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;

/**
 * Диффузия на основе системы Лоренца.
 * <p>
 * Уравнения:
 * </p>
 * <pre>
 *   dx/dt = sigma · (y − x)
 *   dy/dt = x · (rho − z) − y
 *   dz/dt = x · y − beta · z
 * </pre>
 * <p>
 * Классические параметры: {@code sigma = 10}, {@code rho = 28},
 * {@code beta = 8/3}. Интегрируется методом Рунге–Кутты 4-го порядка.
 * На каждый выходной байт делается несколько шагов (чтобы соседние
 * точки траектории не были почти одинаковыми), затем байты из
 * {@code x}, {@code y}, {@code z} XOR-ятся.
 * </p>
 * <p>
 * Параллелизм: блок №0 продолжает прогретое состояние; блоки №1, №2, ...
 * стартуют с детерминированных точек и прогреваются независимо.
 * Результат воспроизводим, но <b>не совпадает</b> побитово с
 * последовательным расчётом — это осознанный компромисс: скорость
 * важнее побитовой идентичности между параллельной и последовательной
 * версиями. Для одного и того же набора параметров результат всегда
 * один и тот же.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 * @see RosslerDiffusion
 * @see DiffusionUtils#rk4
 */
public class LorenzDiffusion implements DiffusionSource {

    /** Шаг интегрирования RK4. */
    private static final double DT = 0.01;
    /** Сколько шагов делать на один выходной байт (для «разнесения» соседних отсчётов). */
    private static final int STEPS_PER_SAMPLE = 3;
    /** Сколько шагов прогрева, чтобы выйти на аттрактор. */
    private static final int BURN_IN_STEPS = 2000;

    /**
     * {@inheritDoc}
     * <p>
     * Ожидаемые параметры:
     * </p>
     * <ul>
     *   <li>{@code "sigma"}, {@code "rho"}, {@code "beta"} — параметры системы
     *       (по умолчанию классические);</li>
     *   <li>{@code "x0"}, {@code "y0"}, {@code "z0"} — начальное состояние;</li>
     *   <li>{@code "sboxSeed"} — seed S-бокса (0 — отключён).</li>
     * </ul>
     *
     * @throws fractalcipher.exception.EncryptionFailedException если
     *         траектория разошлась (см. {@link DiffusionUtils#rk4})
     */
    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        final long seed = params.getOrDefault("seed", 0.0).longValue();
        final Random rnd = new Random(seed);
        final double sigma = params.getOrDefault("sigma", 10.0);
        final double rho = params.getOrDefault("rho", 28.0);
        final double beta = params.getOrDefault("beta", 8.0 / 3.0);

        // x0, y0, z0 — часть ключа. Если не переданы — случайные
        // (в реальном шифровании они ВСЕГДА должны приходить из ключа).
        final double x0 = params.containsKey("x0") ? params.get("x0") : DiffusionUtils.randomCord(rnd) * 2 - 1;
        final double y0 = params.containsKey("y0") ? params.get("y0") : DiffusionUtils.randomCord(rnd) * 2 - 1;
        final double z0 = params.containsKey("z0") ? params.get("z0") : DiffusionUtils.randomCord(rnd) * 2;

        final DiffusionUtils.Deriv f = (final double[] st, final double[] out) -> {
            out[0] = sigma * (st[1] - st[0]);
            out[1] = st[0] * (rho - st[2]) - st[1];
            out[2] = st[0] * st[1] - beta * st[2];
        };

        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Прогрев (для блока №0) — выходим на аттрактор
        final double[] burned = {x0, y0, z0};
        final double[][] burnWork = DiffusionUtils.newRk4Work(3);
        for (int i = 0; i < BURN_IN_STEPS; i++) {
            DiffusionUtils.rk4(burned, DT, f, burnWork);
        }

        final long keyBits = DiffusionUtils.keyBits(x0, y0, z0, sigma, rho, beta);

        // Траектория считается блоками параллельно: блок №0 продолжает прогретое
        // состояние, остальные стартуют со своих ключезависимых точек (с прогревом).
        // У каждого блока свои рабочие массивы RK4.
        final byte[] stream = DiffusionUtils.chainTwoPassParallel(length, chunk -> {
            final double[][] work = DiffusionUtils.newRk4Work(3);
            final double[] s;
            if (chunk == 0) {
                s = burned.clone();
            } else {
                final Random cr = DiffusionUtils.chunkRandom(keyBits, chunk);
                s = new double[]{
                        DiffusionUtils.randomCord(cr) * 2 - 1,
                        DiffusionUtils.randomCord(cr) * 2 - 1,
                        DiffusionUtils.randomCord(cr) * 2
                };
                for (int i = 0; i < BURN_IN_STEPS; i++) {
                    DiffusionUtils.rk4(s, DT, f, work);
                }
            }
            return () -> {
                for (int j = 0; j < STEPS_PER_SAMPLE; j++) {
                    DiffusionUtils.rk4(s, DT, f, work);
                }
                return DiffusionUtils.fracByte(s[0])
                        ^ DiffusionUtils.fracByte(s[1])
                        ^ DiffusionUtils.fracByte(s[2]);
            };
        });

        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    /** @return строковое имя источника — {@code "lorenz"} */
    @Override
    public String getName() {
        return "lorenz";
    }
}