package fractalcipher.model.permutation;


import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

// Перестановка на основе фрактала Burning Ship.
// Формула: z_{n+1} = (|Re(z_n)| + i·|Im(z_n)|)^2 + c
//
// Гибрид Мандельброта и Жюлиа:
//  - c — ФИКСИРОВАННЫЙ параметр (как у Жюлиа), генерируется из seed;
//  - z_0 — координата пикселя (как у Жюлиа);
//  - но перед возведением в квадрат берётся модуль Re и Im —
//    именно это даёт характерную форму "горящего корабля".
public class BurningShipPermutation implements PermutationSource {
    // Сколько максимум итераций делаем, прежде чем считать точку
    // "не убегающей". Как и у Мандельброта/Жюлиа — 300 по умолчанию.
    private static final int DEFAULT_MAX_ITERATIONS = 300;

    // Радиус убегания. Как и у Мандельброта — 2.0.
    private static final double ESCAPE_RADIUS = 2.0;

    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int width = params.getOrDefault("width", (double) length).intValue();
        int height = Math.max(1, length / width);

        long seed = params.getOrDefault("seed", 0.0).longValue();
        Random rnd = new Random(seed);

        // === Область комплексной плоскости для z_0 ===
        // Burning Ship "живёт" в другой области, чем Мандельброт:
        // примерно [-2.5, 1] × [-2, 1]. Диапазоны подобраны так,
        // чтобы захватывать основную структуру фрактала.
        double reMin = params.getOrDefault("reMin", -2.5 - rnd.nextDouble() * 0.5);
        double reMax = params.getOrDefault("reMax",  1.0 + rnd.nextDouble() * 0.5);
        double imMin = params.getOrDefault("imMin", -2.0 - rnd.nextDouble() * 0.5);
        double imMax = params.getOrDefault("imMax",  1.0 + rnd.nextDouble() * 0.5);

        // === КЛЮЧЕВОЕ ОТЛИЧИЕ: фиксированный c (как у Жюлиа) ===
        // c — одно комплексное число для всего изображения.
        // Классический Burning Ship использует c = -1.8 + 0.0i,
        // но для шифрования лучше генерировать из seed, чтобы
        // каждая сессия давала уникальный фрактал.
        double cRe = params.getOrDefault("cRe", -1.8 + rnd.nextDouble() * 1.0);
        double cIm = params.getOrDefault("cIm", -0.1 + rnd.nextDouble() * 0.3);

        // Хаотическое максимальное число итераций — как у Мандельброта.
        int maxIter = params.getOrDefault("maxIter",
                (double) (DEFAULT_MAX_ITERATIONS + rnd.nextInt(700))).intValue();

        // Массив "оценок" — по одной на пиксель.
        double[] values = new double[length];

        final int fWidth = width;
        final int fHeight = height;
        final double fReMin = reMin, fReMax = reMax, fImMin = imMin, fImMax = imMax;
        final int fMaxIter = maxIter;
        final double fCRe = cRe, fCIm = cIm;

        IntStream.range(0,length).parallel().forEach(i-> {
            int row = i / fWidth;
            int col = i % fWidth;

            double zRe0 = fReMin + (fReMax - fReMin) * col / fWidth;
            double zIm0 = fImMin + (fImMax - fImMin) * row / fHeight;

            values[i] = smoothEscapeTimeBurningShip(zRe0, zIm0, fCRe, fCIm, fMaxIter);
        });

        return sortIndicesByValue(values);
    }

    // === Smooth iteration count для Burning Ship ===
    // Отличие от Мандельброта и Жюлиа: перед возведением в квадрат
    // берётся модуль Re и Im.
    private double smoothEscapeTimeBurningShip(double zRe, double zIm, double cRe, double cIm, int maxIter) {
        int iterations = 0;
        double radiusSquared = ESCAPE_RADIUS * ESCAPE_RADIUS;

        while (iterations < maxIter) {
            double zRe2 = zRe * zRe;
            double zIm2 = zIm * zIm;

            if (zRe2 + zIm2 > radiusSquared) {
                break;
            }

            // Формула: (|Re(z)| + i·|Im(z)|)^2 + c
            double absZRe = Math.abs(zRe);
            double absZIm = Math.abs(zIm);

            // Возведение в квадрат модулей
            double absZRe2 = absZRe * absZRe;
            double absZIm2 = absZIm * absZIm;

            double nextZRe = absZRe2 - absZIm2 + cRe;
            double nextZIm = 2.0 * absZRe * absZIm + cIm;

            zRe = nextZRe;
            zIm = nextZIm;
            iterations++;
        }

        if (iterations < maxIter) {
            double magSq = zRe * zRe + zIm * zIm;
            double absZ = Math.sqrt(magSq);
            if (absZ > 1.0) {
                double logZn = Math.log(absZ);
                double nu = Math.log(logZn / Math.log(2.0)) / Math.log(2.0);
                return iterations + 1.0 - nu;
            }
        }
        return iterations;
    }

    // Превращает массив значений в перестановку индексов.
    // Тот же приём, что у Мандельброта и Жюлиа: упаковка (bits, index)
    // в long[] и Arrays.sort(long[]) — без boxing, в разы быстрее
    // чем сортировка Integer[] с компаратором.
    private int[] sortIndicesByValue(double[] values) {
        int n = values.length;

        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            long bits = Double.doubleToLongBits(values[i]);
            // Инвертируем знаковый бит, чтобы отрицательные значения
            // сортировались корректно
            if (bits < 0) bits ^= 0x7FFFFFFFFFFFFFFFL;
            packed[i] = (bits & 0xFFFFFFFF00000000L) | (i & 0xFFFFFFFFL);
        }

        Arrays.parallelSort(packed);

        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int)(packed[i] & 0xFFFFFFFFL);
        }
        return result;
    }

    @Override
    public String getName() {
        return "Горящий корабль";
    }
}
