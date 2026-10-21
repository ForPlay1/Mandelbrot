package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;

public class LogisticMapDiffusion implements DiffusionSource {

    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        final double r = params.getOrDefault("r", 3.99);
        final long seed = params.getOrDefault("seed", 0.0).longValue();
        final Random rnd = new Random(seed);

        // x0 — часть ключа. Если не передан — генерируем случайно
        // (но в реальном шифровании x0 ВСЕГДА должен приходить из ключа).
        // Состояние в массиве, чтобы его можно было менять внутри лямбды.
        final double[] x = {params.getOrDefault("x0", DiffusionUtils.randomCord(rnd))};

        // sboxSeed — опциональный (если 0, S-бокс не применяется)
        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Два прохода со сцеплением (вперёд и назад)
        final byte[] stream = DiffusionUtils.chainTwoPass(length, () -> {
            x[0] = r * x[0] * (1 - x[0]);
            return ((int) (x[0] * 256)) & 0xFF;
        });

        // S-бокс (опционально)
        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    @Override
    public String getName() {
        return "Логистический";
    }
}