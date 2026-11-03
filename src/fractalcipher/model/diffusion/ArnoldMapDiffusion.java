package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;
import fractalcipher.exception.InvalidConfigurationException;

// Диффузия на основе кошачьей карты Арнольда на торе:
//   x_{n+1} = (x_n + a * y_n)          mod 1
//   y_{n+1} = (b * x_n + (a*b + 1) * y_n) mod 1
// При a = b = 1 это классическая карта (x + y, x + 2y) mod 1.
// Определитель матрицы равен 1, поэтому карта сохраняет площадь.
//
// Важно: в double эта карта быстро деградирует (умножение на целые
// матрицы + округление), поэтому считаем в целых числах: тор — это
// решётка 2^64 x 2^64, а mod 2^64 даёт обычное переполнение long.
// Так карта остаётся точной биекцией. Байт берём из СТАРШИХ бит:
// младшие биты в такой арифметике эволюционируют независимо от старших
// и имеют короткий период.
//
// Параллельность: карта линейная, поэтому состояние через n шагов равно
// M^n * s0 (mod 2^64), и M^n считается быстрым возведением в степень.
// Каждый блок стартует с точного места своей части траектории, так что
// результат ТОЧНО совпадает с последовательным расчётом.
public class ArnoldMapDiffusion implements DiffusionSource {

    private static final int BURN_IN = 1000;

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

    // Переполнение long == mod 2^64
    private static void step(final long[] s, final long a, final long b) {
        final long x = s[0];
        final long y = s[1];
        s[0] = x + a * y;
        s[1] = b * x + (a * b + 1) * y;
    }

    // Произведение матриц 2x2 (хранятся строками: {m00, m01, m10, m11}) mod 2^64
    private static long[] mul(final long[] p, final long[] q) {
        return new long[]{
                p[0] * q[0] + p[1] * q[2],
                p[0] * q[1] + p[1] * q[3],
                p[2] * q[0] + p[3] * q[2],
                p[2] * q[1] + p[3] * q[3]
        };
    }

    // M^exp для M = [[1, a], [b, a*b + 1]] mod 2^64
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

    @Override
    public String getName() {
        return "arnold";
    }
}