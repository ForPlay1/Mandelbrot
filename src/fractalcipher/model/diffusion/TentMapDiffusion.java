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
        // Состояние в массиве, чтобы его можно было менять внутри лямбды.
        final double[] x = {params.getOrDefault("x0", DiffusionUtils.randomCord(rnd))};
        if (!(x[0] >= 0.0 && x[0] <= 1.0)) {
            throw new InvalidConfigurationException(
                    "Начальное значение x0 тент-карты должно быть в интервале (0; 1), получено: " + x[0]);
        }

        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Прогрев
        for (int i = 0; i < BURN_IN; i++) {
            x[0] = step(x[0], mu);
        }

        // Два прохода со сцеплением (вперёд и назад)
        final byte[] stream = DiffusionUtils.chainTwoPass(length, () -> {
            x[0] = step(x[0], mu);
            return DiffusionUtils.fracByte(x[0]);
        });

        // S-бокс (опционально)
        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
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