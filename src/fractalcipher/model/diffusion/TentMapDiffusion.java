package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;
import fractalcipher.exception.InvalidConfigurationException;

// Диффузия на основе тент-карты:
//   x_{n+1} = mu * x_n         , если x_n < 0.5
//   x_{n+1} = mu * (1 - x_n)   , если x_n >= 0.5
// Параметр mu должен лежать в (1; 2). При mu = 2 в double карта вырождается:
// умножение на 2 — это просто сдвиг мантиссы, и через ~53 шага x обнуляется.
// Поэтому по умолчанию берём mu чуть меньше 2.
//
// Структура такая же, как у LogisticMapDiffusion и HenonMapDiffusion:
// прогрев, два прохода (вперёд и назад) со сцеплением, опциональный S-бокс.
public class TentMapDiffusion implements DiffusionSource {

    private static final int BURN_IN = 1000;

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

    private static double burnIn(final double start, final double mu) {
        double x = start;
        for (int i = 0; i < BURN_IN; i++) {
            x = step(x, mu);
        }
        return x;
    }

    private static double step(final double x, final double mu) {
        final double nx = (x < 0.5) ? mu * x : mu * (1.0 - x);
        // Защита от вырождения в 0 (фиксированная точка) из-за ошибок округления:
        // детерминированно подмешиваем малую константу, чтобы поток не залип.
        return (nx <= 1e-12) ? 0.123456789 : nx;
    }

    @Override
    public String getName() {
        return "tent";
    }
}