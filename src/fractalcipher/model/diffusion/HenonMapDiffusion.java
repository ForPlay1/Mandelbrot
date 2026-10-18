package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;
import fractalcipher.exception.EncryptionFailedException;

// Диффузия на основе карты Хенона:
//   x_{n+1} = 1 - a*x_n^2 + y_n
//   y_{n+1} = b*x_n
//
// Как свели две величины (x, y) в один байт: на каждом шаге из x и из y
// берётся по байту (из дробной части после умножения на 1e6, чтобы
// отбросить старшие, предсказуемые разряды), и эти байты XOR-ятся.
// Так в поток попадают обе координаты аттрактора.
//
// Структура такая же, как у LogisticMapDiffusion: два прохода (вперёд и
// назад) со сцеплением и опциональный S-бокс.
public class HenonMapDiffusion implements DiffusionSource {

    // Сколько первых итераций отбрасываем, чтобы траектория вышла на аттрактор
    private static final int BURN_IN = 1000;

    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        final long seed = params.getOrDefault("seed", 0.0).longValue();
        final Random rnd = new Random(seed);
        final double a = params.getOrDefault("a", (1.0 + 2.3) * DiffusionUtils.randomCord(rnd) - 2.3);
        final double b = params.getOrDefault("b", (1.5 + 0.5) * DiffusionUtils.randomCord(rnd) - 1.5);

        // x0, y0 — часть ключа. Если не переданы — случайные
        // (в реальном шифровании они ВСЕГДА должны приходить из ключа).
        // Диапазон (-0.5; 0.5) лежит внутри области притяжения аттрактора
        // при классических a=1.4, b=0.3.
        final double x0 = params.getOrDefault("x0", DiffusionUtils.randomCord(rnd) - 0.5);
        final double y0 = params.getOrDefault("y0", DiffusionUtils.randomCord(rnd) - 0.5);

        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        final double[] state = {x0, y0};

        // Прогрев
        for (int i = 0; i < BURN_IN; i++) {
            step(state, a, b);
        }

        // Два прохода со сцеплением (вперёд и назад)
        final byte[] stream = DiffusionUtils.chainTwoPass(length, () -> {
            step(state, a, b);
            return DiffusionUtils.fracByte(state[0]) ^ DiffusionUtils.fracByte(state[1]);
        });

        // S-бокс (опционально)
        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    // Один шаг карты Хенона (state[0] = x, state[1] = y)
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

    @Override
    public String getName() {
        return "henon";
    }
}