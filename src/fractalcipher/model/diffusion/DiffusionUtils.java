package fractalcipher.model.diffusion;

import java.util.Random;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;
import fractalcipher.exception.EncryptionFailedException;

// Общие куски, которые нужны всем картам: сцепление, S-бокс, RK4,
// перевод double в байт, параллельная генерация. Package-private — наружу не торчит.
final class DiffusionUtils {

    // Размер блока траектории при параллельной генерации (в отсчётах).
    // ВАЖНО: константа фиксирована и не зависит от числа ядер, поэтому поток
    // байт одинаков на любой машине. Менять её = менять шифр.
    static final int CHUNK = 1 << 16;

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
    // продолжается (next вызывается 2*length раз). Последовательная версия.
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

    // Параллельная версия chainTwoPass.
    //
    // Дорогая часть — расчёт траектории, а само сцепление (XOR) почти ничего
    // не стоит. Поэтому 2*length отсчётов траектории делятся на блоки по CHUNK,
    // блоки считаются параллельно (у каждого свой независимый генератор, который
    // выдаёт chunkSource по номеру блока), а сцепление потом идёт последовательно
    // по готовому массиву. Если 2*length <= CHUNK, блок один, и результат
    // совпадает с chainTwoPass.
    static byte[] chainTwoPassParallel(final int length, final IntFunction<IntSupplier> chunkSource) {
        if (length > Integer.MAX_VALUE / 2) {
            throw new EncryptionFailedException("Слишком большой размер потока диффузии: " + length);
        }
        final int total = 2 * length;
        final byte[] raw = new byte[total];
        final int chunks = (total + CHUNK - 1) / CHUNK;

        IntStream.range(0, chunks).parallel().forEach(chunk -> {
            final IntSupplier gen = chunkSource.apply(chunk);
            final int from = chunk * CHUNK;
            final int to = Math.min(total, from + CHUNK);
            for (int i = from; i < to; i++) {
                raw[i] = (byte) gen.getAsInt();
            }
        });

        final byte[] stream = new byte[length];

        // Проход 1: вперёд, отсчёты raw[0 .. length-1]
        byte prev = 0;
        for (int i = 0; i < length; i++) {
            stream[i] = (byte) (raw[i] ^ prev);
            prev = stream[i];
        }

        // Проход 2: назад, отсчёты raw[length .. 2*length-1]
        prev = 0;
        for (int i = length - 1; i >= 0; i--) {
            stream[i] = (byte) (stream[i] ^ raw[length + (length - 1 - i)] ^ prev);
            prev = stream[i];
        }

        return stream;
    }

    // Перемешивание 64 бит (финализатор splitmix64)
    static long mix64(final long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    // Число, зависящее от ключа (начальных условий и параметров карты)
    static long keyBits(final double... values) {
        long h = 0x9E3779B97F4A7C15L;
        for (final double v : values) {
            h = mix64(h ^ Double.doubleToLongBits(v));
        }
        return h;
    }

    // Детерминированный генератор для блока chunk: от ключа и номера блока.
    // Из него берутся стартовые условия блоков №1, №2, ... (блок №0 всегда
    // стартует с настоящих начальных условий).
    static Random chunkRandom(final long keyBits, final int chunk) {
        return new Random(mix64(keyBits + 0x9E3779B97F4A7C15L * (chunk + 1L)));
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

    // Рабочие массивы для RK4 (чтобы не выделять их на каждом шаге).
    // У каждого потока/блока должен быть свой комплект.
    static double[][] newRk4Work(final int n) {
        return new double[5][n];
    }

    // Один шаг Рунге–Кутты 4-го порядка, состояние s обновляется на месте
    static void rk4(final double[] s, final double dt, final Deriv f) {
        rk4(s, dt, f, newRk4Work(s.length));
    }

    // То же, но с готовыми рабочими массивами (work из newRk4Work(s.length))
    static void rk4(final double[] s, final double dt, final Deriv f, final double[][] work) {
        final int n = s.length;
        final double[] k1 = work[0];
        final double[] k2 = work[1];
        final double[] k3 = work[2];
        final double[] k4 = work[3];
        final double[] tmp = work[4];

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