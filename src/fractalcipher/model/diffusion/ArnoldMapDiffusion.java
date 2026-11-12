package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;
import fractalcipher.exception.InvalidConfigurationException;

/**
 * Диффузия на основе кошачьей карты Арнольда на торе.
 * <p>
 * Формула:
 * </p>
 * <pre>
 *   x_{n+1} = (x_n + a · y_n)                 mod 1
 *   y_{n+1} = (b · x_n + (a·b + 1) · y_n)     mod 1
 * </pre>
 * <p>
 * При {@code a = b = 1} это классическая карта {@code (x + y, x + 2y) mod 1}.
 * Определитель матрицы равен 1 — карта сохраняет площадь и является
 * биекцией тора в себя.
 * </p>
 * <p>
 * <b>Почему целочисленная арифметика.</b> В {@code double} карта быстро
 * деградирует: умножение на целые и округление «съедают» точность.
 * Поэтому считаем на решётке {@code 2^64 × 2^64}: переполнение
 * {@code long} само даёт {@code mod 2^64}, а карта остаётся точной
 * биекцией. Байт берём из <b>старших</b> бит — младшие в такой
 * арифметике эволюционируют независимо от старших и имеют короткий
 * период.
 * </p>
 * <p>
 * <b>Параллелизм.</b> Карта линейная, поэтому состояние через {@code n}
 * шагов равно {@code M^n · s0 (mod 2^64)}, где {@code M^n} считается
 * быстрым возведением в степень. Каждый блок стартует с точного места
 * своей части траектории — результат <b>точно</b> совпадает с
 * последовательным расчётом (в отличие от карт с прогревами, где
 * блоки стартуют с новых точек).
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 * @see DiffusionUtils#chainTwoPassParallel
 */
public class ArnoldMapDiffusion implements DiffusionSource {

    /** Сколько первых итераций отбрасываем, чтобы уйти от начальной точки. */
    private static final int BURN_IN = 1000;

    /**
     * {@inheritDoc}
     * <p>
     * Ожидаемые параметры:
     * </p>
     * <ul>
     *   <li>{@code "a"}, {@code "b"} — целые ≥ 1 (по умолчанию случайные);</li>
     *   <li>{@code "x0"}, {@code "y0"} — начальная точка в {@code (0; 1)}
     *       (по умолчанию случайные);</li>
     *   <li>{@code "sboxSeed"} — seed S-бокса (0 — отключён).</li>
     * </ul>
     *
     * @throws InvalidConfigurationException если {@code a < 1} или
     *         {@code b < 1}, либо начальная точка совпала с {@code (0, 0)}
     */
    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        final long seed = params.getOrDefault("seed", 0.0).longValue();
        final Random rnd = new Random(seed);
        final long a = params.getOrDefault("a", (double) DiffusionUtils.randomLong(rnd)).longValue();
        final long b = params.getOrDefault("b", (double) DiffusionUtils.randomLong(rnd)).longValue();

        if (a < 1 || b < 1) {
            throw new InvalidConfigurationException(
                    "Параметры a и b карты Арнольда должны быть целыми >= 1");
        }

        // x0, y0 — часть ключа, из интервала (0; 1). Если не переданы —
        // случайные (в реальном шифровании они ВСЕГДА должны приходить из ключа).
        final double x0 = params.containsKey("x0") ? params.get("x0") : DiffusionUtils.randomCord(rnd);
        final double y0 = params.containsKey("y0") ? params.get("y0") : DiffusionUtils.randomCord(rnd);
        if (!(x0 > 0.0 && x0 < 1.0) || !(y0 > 0.0 && y0 < 1.0)) {
            throw new InvalidConfigurationException(
                    "x0 и y0 карты Арнольда должны быть в интервале (0; 1)");
        }

        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Точка на решётке 2^64 (double даёт 53 значащих бита, остальные нули —
        // после прогрева старшие биты всё равно зависят от всех младших)
        final long[] s0 = {
                ((long) (x0 * 0x1p62)) << 1,
                ((long) (y0 * 0x1p62)) << 1
        };
        if (s0[0] == 0 && s0[1] == 0) {
            throw new InvalidConfigurationException(
                    "Начальная точка (0, 0) — неподвижная точка карты, выберите другие x0, y0");
        }

        // Блок №chunk начинается после BURN_IN + chunk*CHUNK шагов от s0:
        // перескакиваем туда матрицей M^n, а не шагаем по одному.
        final byte[] stream = DiffusionUtils.chainTwoPassParallel(length, chunk -> {
            final long steps = BURN_IN + (long) chunk * DiffusionUtils.CHUNK;
            final long[] m = matPow(a, b, steps);
            final long[] s = {
                    m[0] * s0[0] + m[1] * s0[1],
                    m[2] * s0[0] + m[3] * s0[1]
            };
            return () -> {
                step(s, a, b);
                return (int) (s[0] >>> 56) ^ (int) (s[1] >>> 56);
            };
        });

        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    /**
     * Один шаг карты Арнольда. Переполнение {@code long} эквивалентно
     * {@code mod 2^64}.
     *
     * @param s состояние {@code [x, y]} (обновляется на месте)
     * @param a параметр {@code a}
     * @param b параметр {@code b}
     */
    private static void step(final long[] s, final long a, final long b) {
        final long x = s[0];
        final long y = s[1];
        s[0] = x + a * y;
        s[1] = b * x + (a * b + 1) * y;
    }

    /**
     * Умножение матриц 2×2 по модулю {@code 2^64}.
     * <p>
     * Матрицы хранятся построчно: {@code {m00, m01, m10, m11}}.
     * </p>
     *
     * @param p первая матрица
     * @param q вторая матрица
     * @return {@code p · q mod 2^64}
     */
    private static long[] mul(final long[] p, final long[] q) {
        return new long[]{
                p[0] * q[0] + p[1] * q[2],
                p[0] * q[1] + p[1] * q[3],
                p[2] * q[0] + p[3] * q[2],
                p[2] * q[1] + p[3] * q[3]
        };
    }

    /**
     * Быстрое возведение матрицы {@code [[1, a], [b, a·b+1]]} в степень
     * {@code exp} по модулю {@code 2^64}.
     *
     * @param a   параметр {@code a}
     * @param b   параметр {@code b}
     * @param exp показатель (неотрицательный)
     * @return {@code M^exp mod 2^64} в развёрнутом виде
     */
    private static long[] matPow(final long a, final long b, final long exp) {
        long[] result = {1, 0, 0, 1};
        long[] base = {1, a, b, a * b + 1};
        long n = exp;
        while (n > 0) {
            if ((n & 1) == 1) {
                result = mul(result, base);
            }
            base = mul(base, base);
            n >>= 1;
        }
        return result;
    }

    /** @return строковое имя источника — {@code "arnold"} */
    @Override
    public String getName() {
        return "arnold";
    }
}