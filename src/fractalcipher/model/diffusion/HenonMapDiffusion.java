package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;
import fractalcipher.exception.EncryptionFailedException;

/**
 * Диффузия на основе карты Хенона.
 * <p>
 * Формула:
 * </p>
 * <pre>
 *   x_{n+1} = 1 − a · x_n² + y_n
 *   y_{n+1} = b · x_n
 * </pre>
 * <p>
 * <b>Как две координаты сворачиваются в один байт.</b> На каждом шаге
 * из {@code x} и из {@code y} берётся по байту (из дробной части после
 * умножения на {@code 1e6}, чтобы отбросить предсказуемые старшие
 * разряды) — эти байты XOR-ятся. Так в поток попадают обе координаты
 * аттрактора, а не только {@code x}.
 * </p>
 * <p>
 * Структура та же, что у {@link LogisticMapDiffusion}: прогрев, два
 * прохода со сцеплением, опциональный S-бокс.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 * @see LogisticMapDiffusion
 */
public class HenonMapDiffusion implements DiffusionSource {

    /** Сколько первых итераций отбрасываем, чтобы траектория вышла на аттрактор. */
    private static final int BURN_IN = 1000;

    /**
     * Сколько раз пробуем подобрать стартовую точку блока.
     * <p>
     * Не всякая точка квадрата лежит в области притяжения аттрактора
     * Хенона — часть траекторий уходит в бесконечность. Поэтому при
     * расхождении берём следующую точку из того же детерминированного
     * генератора.
     * </p>
     */
    private static final int MAX_START_ATTEMPTS = 100;

    /**
     * {@inheritDoc}
     * <p>
     * Ожидаемые параметры:
     * </p>
     * <ul>
     *   <li>{@code "a"}, {@code "b"} — параметры карты
     *       (по умолчанию в окрестности классических {@code 1.4} и {@code 0.3});</li>
     *   <li>{@code "x0"}, {@code "y0"} — начальная точка;</li>
     *   <li>{@code "sboxSeed"} — seed S-бокса (0 — отключён).</li>
     * </ul>
     *
     * @throws EncryptionFailedException если стартовую точку блока
     *         не удалось подобрать за {@link #MAX_START_ATTEMPTS} попыток
     */
    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        final long seed = params.getOrDefault("seed", 0.0).longValue();
        final Random rnd = new Random(seed);
        final double a = params.getOrDefault("a", 1.3 + DiffusionUtils.randomCord(rnd) * 0.1);
        final double b = params.getOrDefault("b", 0.2 + DiffusionUtils.randomCord(rnd) * 0.2);

        // x0, y0 — часть ключа. Если не переданы — случайные
        // (в реальном шифровании они ВСЕГДА должны приходить из ключа).
        // Диапазон (-0.5; 0.5) лежит внутри области притяжения аттрактора
        // при классических a=1.4, b=0.3.
        final double x0 = params.getOrDefault("x0", -1.0 + DiffusionUtils.randomCord(rnd) * 2.0);
        final double y0 = params.getOrDefault("y0", -0.3 + DiffusionUtils.randomCord(rnd) * 0.6);

        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Прогрев (для блока №0)
        final double[] burned = {x0, y0};
        for (int i = 0; i < BURN_IN; i++) {
            step(burned, a, b);
        }

        final long keyBits = DiffusionUtils.keyBits(x0, y0, a, b);

        // Траектория считается блоками параллельно: блок №0 продолжает прогретое
        // состояние, остальные стартуют со своих ключезависимых точек (с прогревом).
        final byte[] stream = DiffusionUtils.chainTwoPassParallel(length, chunk -> {
            final double[] state = (chunk == 0)
                    ? burned.clone()
                    : chunkStart(keyBits, chunk, a, b);
            return () -> {
                step(state, a, b);
                return DiffusionUtils.fracByte(state[0]) ^ DiffusionUtils.fracByte(state[1]);
            };
        });

        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    /**
     * Подбирает прогретое стартовое состояние для блока {@code chunk > 0}.
     * <p>
     * Пытается до {@link #MAX_START_ATTEMPTS} раз: если траектория от
     * очередной точки разошлась — берёт следующую из того же
     * детерминированного генератора. Результат воспроизводим.
     * </p>
     *
     * @param keyBits хеш ключа
     * @param chunk   номер блока (&gt; 0)
     * @param a       параметр {@code a}
     * @param b       параметр {@code b}
     * @return прогретое состояние {@code [x, y]}
     * @throws EncryptionFailedException если все попытки разошлись
     */
    private static double[] chunkStart(final long keyBits, final int chunk,
                                       final double a, final double b) {
        final Random cr = DiffusionUtils.chunkRandom(keyBits, chunk);
        for (int attempt = 0; attempt < MAX_START_ATTEMPTS; attempt++) {
            final double[] st = {
                    DiffusionUtils.randomCord(cr) - 0.5,
                    DiffusionUtils.randomCord(cr) - 0.5
            };
            try {
                for (int i = 0; i < BURN_IN; i++) {
                    step(st, a, b);
                }
                return st;
            } catch (EncryptionFailedException e) {
                // эта стартовая точка разошлась — пробуем следующую
            }
        }
        throw new EncryptionFailedException(
                "Не удалось подобрать стартовую точку блока для карты Хенона — проверьте параметры a, b");
    }

    /**
     * Один шаг карты Хенона.
     *
     * @param state {@code [x, y]} (обновляется на месте)
     * @param a     параметр {@code a}
     * @param b     параметр {@code b}
     * @throws EncryptionFailedException если траектория разошлась
     */
    private static void step(final double[] state, final double a, final double b) {
        final double x = state[0];
        final double y = state[1];
        final double nx = 1.0 - a * x * x + y;
        final double ny = b * x;

        if (Double.isNaN(nx) || Double.isInfinite(nx) || Math.abs(nx) > 1e6) {
            throw new EncryptionFailedException(
                    "Траектория карты Хенона разошлась — проверьте параметры a, b, x0, y0");
        }

        state[0] = nx;
        state[1] = ny;
    }

    /** @return строковое имя источника — {@code "henon"} */
    @Override
    public String getName() {
        return "henon";
    }
}