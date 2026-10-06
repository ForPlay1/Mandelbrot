package fractalcipher.model.diffusion;

import java.util.Map;
import java.security.SecureRandom;
import java.util.Random;

public class LogisticMapDiffusion implements DiffusionSource {

    @Override
    public byte[] generateStream(int length, Map<String, Double> params) {
        double r = params.getOrDefault("r", 3.99);

        // x0 — часть ключа. Если не передан — генерируем случайно
        // (но в реальном шифровании x0 ВСЕГДА должен приходить из ключа).
        double x;
        if (params.containsKey("x0")) {
            x = params.get("x0");
        } else {
            SecureRandom rnd = new SecureRandom();
            long bits;
            do { bits = rnd.nextLong() >>> 11; } while (bits == 0);
            x = bits / (double) (1L << 53);
        }

        // sboxSeed — опциональный (если 0, S-бокс не применяется)
        long sboxSeed = params.getOrDefault("sboxSeed", 0.0).longValue();

        byte[] stream = new byte[length];

        // === ПРОХОД 1: вперёд со сцеплением ===
        byte prev = 0;
        for (int i = 0; i < length; i++) {
            x = r * x * (1 - x);
            byte chaotic = (byte) (((int) (x * 256)) & 0xFF);
            stream[i] = (byte) (chaotic ^ prev);
            prev = stream[i];
        }

        // === ПРОХОД 2: назад со сцеплением ===
        prev = 0;
        for (int i = length - 1; i >= 0; i--) {
            x = r * x * (1 - x);
            byte chaotic = (byte) (((int) (x * 256)) & 0xFF);
            stream[i] = (byte) (stream[i] ^ chaotic ^ prev);
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
        return "logistic";
    }
}