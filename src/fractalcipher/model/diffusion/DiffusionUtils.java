package fractalcipher.model.diffusion;

import java.util.Random;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;
import fractalcipher.exception.EncryptionFailedException;

/**
 * Общие утилиты для всех реализаций {@link DiffusionSource}.
 * <p>
 * Содержит:
 * </p>
 * <ul>
 *   <li>два прохода со сцеплением (последовательный и параллельный);</li>
 *   <li>преобразование {@code double} в байт ({@link #fracByte});</li>
 *   <li>детерминированные генераторы для блоков параллельной траектории;</li>
 *   <li>опциональный S-бокс;</li>
 *   <li>один шаг метода Рунге–Кутты 4-го порядка для систем ОДУ.</li>
 * </ul>
 * <p>
 * Класс package-private: снаружи пакета не торчит — это «внутренняя
 * кухня» источников диффузии.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 */
final class DiffusionUtils {

    /**
     * Размер блока траектории при параллельной генерации (в отсчётах).
     * <p>
     * <b>Внимание:</b> константа фиксирована и не зависит от числа ядер —
     * это гарантирует, что байтовый поток одинаков на любой машине.
     * Менять её значение = менять шифр (файлы, зашифрованные старой
     * версией, перестанут расшифровываться).
     * </p>
     */
    static final int CHUNK = 1 << 16;

    /** Утилитарный класс — инстанцирование не нужно. */
    private DiffusionUtils() {}

    /**
     * Правая часть системы ОДУ: по состоянию {@code s} записывает
     * производную в {@code out}.
     * <p>
     * Функциональный интерфейс, чтобы разные системы (Лоренц, Рёсслер)
     * могли использовать общий интегратор {@link #rk4}.
     * </p>
     */
    interface Deriv {
        /**
         * @param s   текущее состояние
         * @param out массив, куда записать производную
         */
        void apply(final double[] s, final double[] out);
    }

    /**
     * Извлекает байт (0..255) из дробной части {@code |v| * 1e6}.
     * <p>
     * Умножение на {@code 1e6} отбрасывает старшие, предсказуемые
     * разряды числа — так байт зависит от «шумной» дробной части,
     * а не от порядка величины {@code v}.
     * </p>
     *
     * @param v значение (например, координата траектории)
     * @return байт 0..255
     */
    static int fracByte(final double v) {
        final double scaled = Math.abs(v) * 1e6;
        final double frac = scaled - Math.floor(scaled);
        return ((int) (frac * 256)) & 0xFF;
    }

    /**
     * Генерирует случайное число в интервале {@code (0; 1)}.
     * <p>
     * Ноль исключается: для некоторых карт нулевая точка является
     * неподвижной, и траектория выродится.
     * </p>
     *
     * @param rnd генератор
     * @return число в {@code (0; 1)}
     */
    static double randomCord(final Random rnd) {
        long bits;
        do { bits = rnd.nextLong() >>> 11; } while (bits == 0);
        return bits / (double) (1L << 53);
    }

    /**
     * Генерирует случайное целое число в диапазоне {@code [1; Long.MAX_VALUE]}.
     * <p>
     * Используется для параметров карты Арнольда ({@code a}, {@code b}),
     * которые должны быть целыми ≥ 1.
     * </p>
     *
     * @param rnd генератор
     * @return целое ≥ 1
     */
    static long randomLong(final Random rnd) {
        long v;
        do {
            v = rnd.nextLong() >>> 1;   // 0 .. 2^63 - 1 = Long.MAX_VALUE
        } while (v == 0);               // исключаем 0, чтобы получить >= 1
        return v;                       // [1, Long.MAX_VALUE]
    }

    /**
     * Последовательная версия двух проходов со сцеплением:
     * сначала вперёд, затем назад.
     * <p>
     * Смысл сцепления: каждый следующий байт потока зависит от
     * предыдущего, поэтому изменение любого пикселя «размазывается»
     * по всему шифротексту. Траектория продолжается
     * ({@code next} вызывается {@code 2*length} раз).
     * </p>
     * <p>
     * Используется как эталон при тестировании параллельной версии
     * {@link #chainTwoPassParallel}.
     * </p>
     *
     * @param length число байт в результирующем потоке
     * @param next   поставщик отсчётов траектории (значения 0..255)
     * @return поток байт длиной {@code length}
     */
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

    /**
     * Параллельная версия {@link #chainTwoPass}.
     * <p>
     * Дорогая часть — расчёт траектории, а само сцепление (XOR) почти
     * ничего не стоит. Поэтому {@code 2*length} отсчётов траектории
     * делятся на блоки по {@link #CHUNK}, блоки считаются параллельно
     * (у каждого свой независимый генератор, который выдаёт
     * {@code chunkSource} по номеру блока), а сцепление потом идёт
     * последовательно по готовому массиву.
     * </p>
     * <p>
     * Если {@code 2*length <= CHUNK}, блок один, и результат совпадает
     * с {@link #chainTwoPass}.
     * </p>
     *
     * @param length      число байт в результирующем потоке
     * @param chunkSource фабрика генераторов: по номеру блока выдаёт
     *                    {@link IntSupplier}, который начинает
     *                    траекторию с правильного места
     * @return поток байт длиной {@code length}
     * @throws EncryptionFailedException если {@code length} слишком
     *         велик для {@code int} (защита от переполнения)
     */
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

    /**
     * Финализатор splitmix64: качественное перемешивание 64 бит.
     * <p>
     * Используется как «лавина» для получения независимых значений
     * из ключа и номера блока.
     * </p>
     *
     * @param value исходное значение
     * @return перемешанное значение
     */
    static long mix64(final long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /**
     * Хеш от набора {@code double}-значений (обычно — начальных условий
     * и параметров карты).
     * <p>
     * Результат используется как seed для
     * {@link #chunkRandom(long, int)}, чтобы блоки параллельной
     * траектории получили воспроизводимые стартовые точки.
     * </p>
     *
     * @param values значения, из которых строится хеш
     * @return 64-битное число, зависящее от всех входов
     */
    static long keyBits(final double... values) {
        long h = 0x9E3779B97F4A7C15L;
        for (final double v : values) {
            h = mix64(h ^ Double.doubleToLongBits(v));
        }
        return h;
    }

    /**
     * Детерминированный генератор для блока {@code chunk}.
     * <p>
     * Блок №0 всегда стартует с «настоящих» начальных условий; для
     * блоков №1, №2, ... этот генератор выдаёт псевдослучайные стартовые
     * точки, зависящие от ключа и номера блока. Так параллельная
     * генерация остаётся полностью воспроизводимой.
     * </p>
     *
     * @param keyBits хеш ключа (см. {@link #keyBits(double...)})
     * @param chunk   номер блока (1-based для «продолжений»)
     * @return генератор для {@code chunk}
     */
    static Random chunkRandom(final long keyBits, final int chunk) {
        return new Random(mix64(keyBits + 0x9E3779B97F4A7C15L * (chunk + 1L)));
    }

    /**
     * Применяет опциональный S-бокс к потоку байт.
     * <p>
     * Если {@code seed == 0} — ничего не делает (S-бокс отключён).
     * Иначе строит перестановку 0..255 методом Фишера–Йетса на
     * {@link Random#Random(long)} от {@code seed} и заменяет каждый
     * байт по таблице.
     * </p>
     *
     * @param stream поток байт (изменяется на месте)
     * @param seed   seed S-бокса (0 — отключено)
     */
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

    /**
     * Создаёт рабочие массивы для {@link #rk4}.
     * <p>
     * Возвращает {@code double[5][n]} — пять массивов размера {@code n}:
     * коэффициенты {@code k1..k4} и временное состояние. У каждого
     * потока / блока должен быть свой комплект — иначе гонка данных.
     * </p>
     *
     * @param n размерность системы ОДУ
     * @return готовые к использованию рабочие массивы
     */
    static double[][] newRk4Work(final int n) {
        return new double[5][n];
    }

    /**
     * Один шаг Рунге–Кутты 4-го порядка (без внешних рабочих массивов —
     * создаёт их внутри).
     * <p>
     * Удобно для одиночных вызовов; в горячих циклах используйте
     * перегрузку с {@code work}, чтобы не выделять память на каждом шаге.
     * </p>
     *
     * @param s  состояние (обновляется на месте)
     * @param dt шаг интегрирования
     * @param f  правая часть системы ОДУ
     */
    static void rk4(final double[] s, final double dt, final Deriv f) {
        rk4(s, dt, f, newRk4Work(s.length));
    }

    /**
     * Один шаг Рунге–Кутты 4-го порядка с готовыми рабочими массивами.
     * <p>
     * После шага проверяет, что траектория не разошлась
     * ({@code NaN}/{@code Infinity}/{@code |s| > 1e6}) — иначе бросает
     * {@link EncryptionFailedException}. Это защищает от «зависания»
     * в бесконечном цикле при неудачных параметрах.
     * </p>
     *
     * @param s    состояние (обновляется на месте)
     * @param dt   шаг интегрирования
     * @param f    правая часть системы ОДУ
     * @param work рабочие массивы (см. {@link #newRk4Work(int)})
     * @throws EncryptionFailedException если траектория разошлась
     */
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