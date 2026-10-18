package fractalcipher.model.diffusion;

import java.util.Random;
import java.util.function.IntSupplier;
import fractalcipher.exception.EncryptionFailedException;

// Общие куски, которые нужны всем картам: сцепление, S-бокс, RK4,
// перевод double в байт. Package-private — наружу не торчит.
final class DiffusionUtils {

    private DiffusionUtils() {}

    // Правая часть системы ОДУ: по состоянию s записывает производную в out
    interface Deriv {
        void apply(final double[] s, final double[] out);
    }

    // Байт (0..255) из дробной части v*1e6 — отбрасываем старшие,
    // предсказуемые разряды
    static int fracByte(final double v) {
        final double scaled = Math.abs(v) * 1e6;
        final double frac = scaled - Math.floor(scaled);
        return ((int) (frac * 256)) & 0xFF;
    }

    // Случайное число в (0; 1)
    static double randomCord(final Random rnd) {
        long bits;
        do { bits = rnd.nextLong() >>> 11; } while (bits == 0);
        return bits / (double) (1L << 53);
    }

    //Случайное целое число от 1
    static long randomLong(final Random rnd) {
        long v;
        do {
            v = rnd.nextLong() >>> 1;   // 0 .. 2^63 - 1 = Long.MAX_VALUE
        } while (v == 0);               // исключаем 0, чтобы получить >= 1
        return v;                       // [1, Long.MAX_VALUE]
    }

    // Два прохода со сцеплением: вперёд, затем назад. Траектория
    // продолжается (next вызывается 2*length раз).
    static byte[] chainTwoPass(final int length, final IntSupplier next) {
        final byte[] stream = new byte[length];

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
    static void applySBox(final byte[] stream, final long seed) {
        if (seed == 0L) return;

        final int[] sbox = new int[256];
        for (int i = 0; i < 256; i++) sbox[i] = i;
        final Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            final int j = rnd.nextInt(i + 1);
            final int tmp = sbox[i];
            sbox[i] = sbox[j];
            sbox[j] = tmp;
        }

        for (int i = 0; i < stream.length; i++) {
            stream[i] = (byte) sbox[stream[i] & 0xFF];
        }
    }

    // Один шаг Рунге–Кутты 4-го порядка, состояние s обновляется на месте
    static void rk4(final double[] s, final double dt, final Deriv f) {
        final int n = s.length;
        final double[] k1 = new double[n];
        final double[] k2 = new double[n];
        final double[] k3 = new double[n];
        final double[] k4 = new double[n];
        final double[] tmp = new double[n];

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
                throw new EncryptionFailedException(
                        "Траектория системы разошлась — проверьте параметры и начальные условия");
            }
        }
    }
}