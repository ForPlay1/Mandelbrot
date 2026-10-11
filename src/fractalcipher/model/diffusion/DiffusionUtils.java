package fractalcipher.model.diffusion;

import java.security.SecureRandom;
import java.util.Random;
import java.util.function.IntSupplier;

// Общие куски, которые нужны всем картам: сцепление, S-бокс, RK4,
// перевод double в байт. Package-private — наружу не торчит.
final class DiffusionUtils {

    private DiffusionUtils() {}

    // Правая часть системы ОДУ: по состоянию s записывает производную в out
    interface Deriv {
        void apply(double[] s, double[] out);
    }

    // Байт (0..255) из дробной части v*1e6 — отбрасываем старшие,
    // предсказуемые разряды
    static int fracByte(double v) {
        double scaled = Math.abs(v) * 1e6;
        double frac = scaled - Math.floor(scaled);
        return ((int) (frac * 256)) & 0xFF;
    }

    // Случайное число в (0; 1)
    static double randomUnit(long a) {
        Random rnd = new Random(a);
        long bits;
        do { bits = rnd.nextLong() >>> 11; } while (bits == 0);
        return bits / (double) (1L << 53);
    }

    // Два прохода со сцеплением: вперёд, затем назад. Траектория
    // продолжается (next вызывается 2*length раз).
    static byte[] chainTwoPass(int length, IntSupplier next) {
        byte[] stream = new byte[length];

        byte prev = 0;
        for (int i = 0; i < length; i++) {
            stream[i] = (byte) (next.getAsInt() ^ prev);
            prev = stream[i];
        }

        prev = 0;
        for (int i = length - 1; i >= 0; i--) {
            stream[i] = (byte) (stream[i] ^ next.getAsInt() ^ prev);
            prev = stream[i];
        }

        return stream;
    }

    // Опциональный S-бокс (если seed == 0 — ничего не делает)
    static void applySBox(byte[] stream, long seed) {
        if (seed == 0L) return;

        int[] sbox = new int[256];
        for (int i = 0; i < 256; i++) sbox[i] = i;
        Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = sbox[i];
            sbox[i] = sbox[j];
            sbox[j] = tmp;
        }

        for (int i = 0; i < stream.length; i++) {
            stream[i] = (byte) sbox[stream[i] & 0xFF];
        }
    }

    // Один шаг Рунге–Кутты 4-го порядка, состояние s обновляется на месте
    static void rk4(double[] s, double dt, Deriv f) {
        int n = s.length;
        double[] k1 = new double[n];
        double[] k2 = new double[n];
        double[] k3 = new double[n];
        double[] k4 = new double[n];
        double[] tmp = new double[n];

        f.apply(s, k1);
        for (int i = 0; i < n; i++) tmp[i] = s[i] + 0.5 * dt * k1[i];
        f.apply(tmp, k2);
        for (int i = 0; i < n; i++) tmp[i] = s[i] + 0.5 * dt * k2[i];
        f.apply(tmp, k3);
        for (int i = 0; i < n; i++) tmp[i] = s[i] + dt * k3[i];
        f.apply(tmp, k4);

        for (int i = 0; i < n; i++) {
            s[i] += dt / 6.0 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
            if (Double.isNaN(s[i]) || Double.isInfinite(s[i]) || Math.abs(s[i]) > 1e6) {
                throw new IllegalStateException(
                        "Траектория системы разошлась — проверьте параметры и начальные условия");
            }
        }
    }
}