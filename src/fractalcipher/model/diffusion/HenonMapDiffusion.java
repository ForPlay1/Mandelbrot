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
    public byte[] generateStream(int length, Map<String, Double> params) {
        long seed = params.getOrDefault("seed",0.0).longValue();
        Random rnd = new Random(seed);
        double a = params.getOrDefault("a", (1.0+2.3)*DiffusionUtils.randomCord(rnd)-2.3);
        double b = params.getOrDefault("b", (1.5+0.5)*DiffusionUtils.randomCord(rnd)-1.5);

        // x0, y0 — часть ключа. Если не переданы — случайные
        // (в реальном шифровании они ВСЕГДА должны приходить из ключа).
        // Диапазон (-0.5; 0.5) лежит внутри области притяжения аттрактора
        // при классических a=1.4, b=0.3.
        double x0 = params.getOrDefault("x0",DiffusionUtils.randomCord(rnd)-0.5);
        double y0 = params.getOrDefault("y0",DiffusionUtils.randomCord(rnd)-0.5);

        long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        double[] state = {x0, y0};

        // Прогрев
        for (int i = 0; i < BURN_IN; i++) {
            step(state, a, b);
        }

        byte[] stream = new byte[length];

        // === ПРОХОД 1: вперёд со сцеплением ===
        byte prev = 0;
        for (int i = 0; i < length; i++) {
            step(state, a, b);
            stream[i] = (byte) (nextByte(state) ^ prev);
            prev = stream[i];
        }

        // === ПРОХОД 2: назад со сцеплением ===
        prev = 0;
        for (int i = length - 1; i >= 0; i--) {
            step(state, a, b);
            stream[i] = (byte) (stream[i] ^ nextByte(state) ^ prev);
            prev = stream[i];
        }

        // === S-бокс (опционально) ===
        if (sboxSeed != 0L) {
            int[] sbox = buildSBox(sboxSeed);
            for (int i = 0; i < length; i++) {
                stream[i] = (byte) sbox[stream[i] & 0xFF];
            }
        }

        return stream;
    }

    // Один шаг карты Хенона (state[0] = x, state[1] = y)
    private static void step(double[] state, double a, double b) {
        double x = state[0];
        double y = state[1];
        double nx = 1.0 - a * x * x + y;
        double ny = b * x;

        if (Double.isNaN(nx) || Double.isInfinite(nx) || Math.abs(nx) > 1e6) {
            throw new EncryptionFailedException(
                    "Траектория карты Хенона разошлась — проверьте параметры a, b, x0, y0");
        }

        state[0] = nx;
        state[1] = ny;
    }

    // Один байт из пары (x, y): XOR байтов, взятых из x и из y
    private static byte nextByte(double[] state) {
        return (byte) (fracByte(state[0]) ^ fracByte(state[1]));
    }

    private static int fracByte(double v) {
        double scaled = Math.abs(v) * 1e6;
        double frac = scaled - Math.floor(scaled);
        return ((int) (frac * 256)) & 0xFF;
    }

    private static int[] buildSBox(long seed) {
        int[] sbox = new int[256];
        for (int i = 0; i < 256; i++) sbox[i] = i;
        Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = sbox[i];
            sbox[i] = sbox[j];
            sbox[j] = tmp;
        }
        return sbox;
    }

    @Override
    public String getName() {
        return "henon";
    }
}