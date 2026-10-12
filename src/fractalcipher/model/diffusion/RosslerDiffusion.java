package fractalcipher.model.diffusion;

import java.util.Map;
import java.util.Random;

// Диффузия на основе системы Рёсслера:
//   dx/dt = -y - z
//   dy/dt = x + a * y
//   dz/dt = b + z * (x - c)
// Классические параметры: a = 0.2, b = 0.2, c = 5.7.
// Интегрируем методом Рунге–Кутты 4-го порядка, на каждый байт —
// несколько шагов, байты из x, y и z объединяются через XOR.
public class RosslerDiffusion implements DiffusionSource {

    private static final double DT = 0.02;
    private static final int STEPS_PER_SAMPLE = 3;
    private static final int BURN_IN_STEPS = 3000;

    @Override
    public byte[] generateStream(int length, Map<String, Double> params) {
        final double a = params.getOrDefault("a", 0.2);
        final double b = params.getOrDefault("b", 0.2);
        final double c = params.getOrDefault("c", 5.7);

        final long seed = params.getOrDefault("seed",0.0).longValue();
        Random rnd = new Random(seed);
        // x0, y0, z0 — часть ключа. Если не переданы — случайные
        // (в реальном шифровании они ВСЕГДА должны приходить из ключа).
        double x0 = params.containsKey("x0") ? params.get("x0") : DiffusionUtils.randomCord(rnd) * 2 - 1;
        double y0 = params.containsKey("y0") ? params.get("y0") : DiffusionUtils.randomCord(rnd) * 2 - 1;
        double z0 = params.containsKey("z0") ? params.get("z0") : DiffusionUtils.randomCord(rnd) * 2;

        final double[] s = {x0, y0, z0};

        DiffusionUtils.Deriv f = (st, out) -> {
            out[0] = -st[1] - st[2];
            out[1] = st[0] + a * st[1];
            out[2] = b + st[2] * (st[0] - c);
        };

        long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Прогрев — выходим на аттрактор
        for (int i = 0; i < BURN_IN_STEPS; i++) {
            DiffusionUtils.rk4(s, DT, f);
        }

        byte[] stream = DiffusionUtils.chainTwoPass(length, () -> {
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
        return "rossler";
    }
}