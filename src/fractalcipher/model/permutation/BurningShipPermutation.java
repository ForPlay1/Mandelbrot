package fractalcipher.model.permutation;

import fractalcipher.model.Complex;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

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
        Complex c = new Complex(cRe, cIm);

        // Хаотическое максимальное число итераций — как у Мандельброта.
        int maxIter = params.getOrDefault("maxIter",
                (double) (DEFAULT_MAX_ITERATIONS + rnd.nextInt(700))).intValue();

        // Массив "оценок" — по одной на пиксель.
        double[] values = new double[length];

        for (int i = 0; i < length; i++) {
            int row = i / width;
            int col = i % width;

            // z_0 — координата пикселя (как у Жюлиа).
            double re = reMin + (reMax - reMin) * col / width;
            double im = imMin + (imMax - imMin) * row / height;
            Complex z0 = new Complex(re, im);

            values[i] = smoothEscapeTimeBurningShip(z0, c, maxIter);
        }

        return sortIndicesByValue(values);
    }

    // === Smooth iteration count для Burning Ship ===
    // Отличие от Мандельброта и Жюлиа: перед возведением в квадрат
    // берётся модуль Re и Im.
    private double smoothEscapeTimeBurningShip(Complex z0, Complex c, int maxIter) {
        Complex z = z0; // стартуем с z_0 (как у Жюлиа)
        int iterations = 0;
        double radiusSquared = ESCAPE_RADIUS * ESCAPE_RADIUS;

        while (iterations < maxIter && z.absSquared() <= radiusSquared) {
            // === ГЛАВНОЕ ОТЛИЧИЕ: модули перед возведением в квадрат ===
            // Формула: (|Re(z)| + i·|Im(z)|)^2 + c
            Complex absZ = new Complex(Math.abs(z.re), Math.abs(z.im));
            z = absZ.multiply(absZ).add(c);
            iterations++;
        }

        // Smooth iteration count — та же формула, что у Мандельброта/Жюлиа.
        if (iterations < maxIter) {
            double absZ = Math.sqrt(z.absSquared());
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

        Arrays.sort(packed);

        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int)(packed[i] & 0xFFFFFFFFL);
        }
        return result;
    }

    @Override
    public String getName() {
        return "burning ship";
    }
}
