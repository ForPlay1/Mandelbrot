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
        final long[] s = {
                ((long) (x0 * 0x1p62)) << 1,
                ((long) (y0 * 0x1p62)) << 1
        };
        if (s[0] == 0 && s[1] == 0) {
            throw new InvalidConfigurationException(
                    "Начальная точка (0, 0) — неподвижная точка карты, выберите другие x0, y0");
        }

        for (int i = 0; i < BURN_IN; i++) {
            step(s, a, b);
        }

        final byte[] stream = DiffusionUtils.chainTwoPass(length, () -> {
            step(s, a, b);
            return (int) (s[0] >>> 56) ^ (int) (s[1] >>> 56);
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

    @Override
    public String getName() {
        return "Арнольд";
    }
}