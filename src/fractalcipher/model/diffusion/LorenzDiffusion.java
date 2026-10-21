package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;

// Диффузия на основе системы Лоренца:
//   dx/dt = sigma * (y - x)
//   dy/dt = x * (rho - z) - y
//   dz/dt = x * y - beta * z
// Классические параметры: sigma = 10, rho = 28, beta = 8/3.
// Интегрируем методом Рунге–Кутты 4-го порядка. На каждый выходной байт
// делаем несколько шагов (чтобы соседние точки не были почти одинаковыми)
// и XOR-им байты, взятые из x, y и z.
public class LorenzDiffusion implements DiffusionSource {

    private static final double DT = 0.01;
    private static final int STEPS_PER_SAMPLE = 3;
    private static final int BURN_IN_STEPS = 2000;

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

        final double[] s = {x0, y0, z0};

        final DiffusionUtils.Deriv f = (final double[] st, final double[] out) -> {
            out[0] = sigma * (st[1] - st[0]);
            out[1] = st[0] * (rho - st[2]) - st[1];
            out[2] = st[0] * st[1] - beta * st[2];
        };

        final long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Прогрев — выходим на аттрактор
        for (int i = 0; i < BURN_IN_STEPS; i++) {
            DiffusionUtils.rk4(s, DT, f);
        }

        final byte[] stream = DiffusionUtils.chainTwoPass(length, () -> {
            for (int k = 0; k < STEPS_PER_SAMPLE; k++) {
                DiffusionUtils.rk4(s, DT, f);
            }
            return DiffusionUtils.fracByte(s[0])
                    ^ DiffusionUtils.fracByte(s[1])
                    ^ DiffusionUtils.fracByte(s[2]);
        });

        DiffusionUtils.applySBox(stream, sboxSeed);
        return stream;
    }

    @Override
    public String getName() {
        return "Лоренц";
    }
}