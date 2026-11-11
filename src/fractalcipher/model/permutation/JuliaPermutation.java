package fractalcipher.model.permutation;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Источник перестановки на основе множества Жюлиа.
 * <p>
 * Формула та же, что у Мандельброта ({@code z = z^2 + c}), но логика
 * обратная: {@code c} — фиксированный параметр ключа (одно число для всего изображения),
 * а по изображению "бегает" именно {@code z_0} (стартовая точка), а не {@code c}.
 * </p>
 * @see PermutationSource
 * @see MandelbrotPermutation
 * @see BurningShipPermutation
 */
public class JuliaPermutation implements PermutationSource {

    /** Максимальное количество итераций по умолчанию. */
    private static final int DEFAULT_MAX_ITERATIONS = 300;

    /** Радиус убегания. */
    private static final double ESCAPE_RADIUS = 2.0;

    /**
     * Генерирует перестановку на основе множества Жюлиа.
     *
     * @param length Количество пикселей.
     * @param params Параметры (width, seed, границы области, cRe, cIm, maxIter, isParallel).
     * @return Массив перестановок индексов.
     */
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int width = params.getOrDefault("width", (double)length).intValue();
        int height = Math.max(1,length/width);

        long seed = params.getOrDefault("seed",0.0).longValue();
        Random rnd = new Random(seed);

        // Область комплексной плоскости для z_0
        double reMin = params.getOrDefault("reMin", -2.0 - rnd.nextDouble() * 0.5);
        double reMax = params.getOrDefault("reMax", 1.0 + rnd.nextDouble() * 0.5);
        double imMin = params.getOrDefault("imMin", -1.5 - rnd.nextDouble() * 0.5);
        double imMax = params.getOrDefault("imMax", 1.5 + rnd.nextDouble() * 0.5);

        // КЛЮЧЕВОЕ ОТЛИЧИЕ: c — фиксированное комплексное число для всего изображения.
        double cRe = params.getOrDefault("cRe", -0.8 + rnd.nextDouble() * 1.6);
        double cIm = params.getOrDefault("cIm", -0.8 + rnd.nextDouble() * 1.6);

        int maxIter = params.getOrDefault("maxIter",(double) (DEFAULT_MAX_ITERATIONS + rnd.nextInt(700))).intValue();
        double[] values = new double[length];

        boolean isParallel = params.getOrDefault("isParallel",0.0) == 1.0;

        final int fWidth = width;
        final int fHeight = height;
        final double fReMin = reMin, fReMax = reMax, fImMin = imMin, fImMax = imMax;
        final int fMaxIter = maxIter;
        final double fCRe = cRe, fCIm = cIm;

        IntStream stream = IntStream.range(0,length);
        if(isParallel) {
            stream = stream.parallel();
        }

        stream.forEach(i -> {
            int row = i / fWidth;
            int col = i % fWidth;

            // z0 — координата точки
            double zRe0 = fReMin + (fReMax - fReMin) * col / fWidth;
            double zIm0 = fImMin + (fImMax - fImMin) * row / fHeight;

            values[i] = smoothEscapeTimeJulia(zRe0, zIm0, fCRe, fCIm, fMaxIter);
        });
        return sortIndicesByValue(values,isParallel);
    }

    /**
     * Вычисляет smooth iteration count для Жюлиа.
     * <p>
     * Отличие от Мандельброта: {@code z_0} — входная точка, {@code c} — фиксирован.
     * </p>
     */
    private double smoothEscapeTimeJulia(double zRe, double zIm, double cRe, double cIm, int maxIter) {
        int iterations = 0;
        double radiusSquared = ESCAPE_RADIUS * ESCAPE_RADIUS;

        while (iterations < maxIter) {
            double zRe2 = zRe * zRe;
            double zIm2 = zIm * zIm;

            if (zRe2 + zIm2 > radiusSquared) {
                break;
            }

            // z = z^2 + c
            double nextZRe = zRe2 - zIm2 + cRe;
            double nextZIm = 2.0 * zRe * zIm + cIm;

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

    /**
     * Превращает массив весов в массив перестановок индексов.
     * Использует упаковку {@code double} в {@code long} для быстрой сортировки.
     */
    private int[] sortIndicesByValue(double[] values,boolean isParallel) {
        int n = values.length;

        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            // Превращаем double в сортируемый long через битовое представление
            long bits = Double.doubleToLongBits(values[i]);
            // Инвертируем знаковый бит для корректной сортировки отрицательных
            if (bits < 0) bits ^= 0x7FFFFFFFFFFFFFFFL;
            // Старшие 32 бита — значение, младшие — индекс
            packed[i] = (bits & 0xFFFFFFFF00000000L) | (i & 0xFFFFFFFFL);
        }

        if(isParallel) {
            Arrays.parallelSort(packed);
        }
        else{
            Arrays.sort(packed);
        }

        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int)(packed[i] & 0xFFFFFFFFL);
        }
        return result;
    }

    /**
     * @return Строка "julia".
     */
    @Override
    public String getName() {
        return "julia";
    }
}