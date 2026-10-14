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
    public byte[] generateStream(int length, Map<String, Double> params) {
        final long seed = params.getOrDefault("seed",0.0).longValue();
        Random rnd = new Random(seed);
        double mu = params.getOrDefault("mu", DiffusionUtils.randomCord(rnd)+1.0);
        if (!(mu >= 1.0 && mu <= 2.0)) {
            throw new InvalidConfigurationException(
                    "Параметр mu тент-карты должен быть в интервале (1; 2), получено: " + mu);
        }

        // x0 — часть ключа. Если не передан — случайный
        // (в реальном шифровании x0 ВСЕГДА должен приходить из ключа).
        double x = params.getOrDefault("x0",DiffusionUtils.randomCord(rnd));
        if (!(x >= 0.0 && x <= 1.0)) {
            throw new InvalidConfigurationException(
                    "Начальное значение x0 тент-карты должно быть в интервале (0; 1), получено: " + x);
        }

        long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        // Прогрев
        for (int i = 0; i < BURN_IN; i++) {
            x = step(x, mu);
        }

        byte[] stream = new byte[length];

        // === ПРОХОД 1: вперёд со сцеплением ===
        byte prev = 0;
        for (int i = 0; i < length; i++) {
            x = step(x, mu);
            stream[i] = (byte) (toByte(x) ^ prev);
            prev = stream[i];
        }

        // === ПРОХОД 2: назад со сцеплением ===
        prev = 0;
        for (int i = length - 1; i >= 0; i--) {
            x = step(x, mu);
            stream[i] = (byte) (stream[i] ^ toByte(x) ^ prev);
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

    private static double step(double x, double mu) {
        double nx = (x < 0.5) ? mu * x : mu * (1.0 - x);
        // Защита от вырождения в 0 (фиксированная точка) из-за ошибок округления:
        // детерминированно подмешиваем малую константу, чтобы поток не залип.
        if (nx <= 1e-12) {
            nx = 0.123456789;
        }
        return nx;
    }

    // Байт из дробной части x*1e6 — отбрасываем старшие, предсказуемые разряды
    private static byte toByte(double x) {
        double scaled = x * 1e6;
        double frac = scaled - Math.floor(scaled);
        return (byte) (((int) (frac * 256)) & 0xFF);
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
        return "tent";
    }
}