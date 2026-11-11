package fractalcipher.model.permutation;

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.Arrays;

/**
 * Источник перестановки на основе множества Мандельброта.
 * <p>
 * Идея: каждому пикселю (row, col) ставим в соответствие точку {@code c}
 * на комплексной плоскости. Считаем escape-time (сколько итераций
 * {@code z = z^2 + c} нужно, чтобы {@code z} "улетело" за границу),
 * и это число используем как ключ сортировки — куда пиксель переедет.
 * </p>
 * @see PermutationSource
 * @see BurningShipPermutation
 * @see JuliaPermutation
 */
public class MandelbrotPermutation implements PermutationSource {

    /**
     * Максимальное количество итераций по умолчанию.
     * Чем больше — тем точнее, но и медленнее.
     */
    private static final int DEFAULT_MAX_ITERATIONS = 300;

    /**
     * Радиус убегания. Если {@code |z|} превышает это значение,
     * точка считается улетевшей в бесконечность.
     */
    private static final double ESCAPE_RADIUS = 2.0;

    /**
     * Генерирует перестановку на основе множества Мандельброта.
     *
     * @param length Количество пикселей (ширина × высота).
     * @param params Параметры (width, seed, границы области, maxIter, isParallel).
     * @return Массив перестановок индексов.
     */
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        // Извлекаем ширину изображения (по умолчанию считаем, что изображение в одну строку)
        int width = params.getOrDefault("width", (double) length).intValue();
        // Вычисляем высоту, защищаясь от деления на ноль
        int height = Math.max(1, length / width);
        // Извлекаем seed для детерминированной генерации случайных параметров
        long seed = params.getOrDefault("seed", 0.0).longValue();
        Random rnd = new Random(seed);

        // Диапазоны подобраны так, чтобы захватывать интересные участки
        // множества Мандельброта (границу и окрестности).
        double reMin = params.getOrDefault("reMin", -2.0 - rnd.nextDouble() * 0.5);
        double reMax = params.getOrDefault("reMax", 1.0 + rnd.nextDouble() * 0.5);
        double imMin = params.getOrDefault("imMin", -1.5 - rnd.nextDouble() * 0.5);
        double imMax = params.getOrDefault("imMax", 1.5 + rnd.nextDouble() * 0.5);

        // Хаотическое максимальное число итераций
        int maxIter = params.getOrDefault("maxIter",
                (double) (DEFAULT_MAX_ITERATIONS + rnd.nextInt(700))).intValue();

        boolean isParallel = params.getOrDefault("isParallel",0.0) == 1.0;

        // Массив "оценок" по одной на пиксель — сюда положим escape-time.
        double[] values = new double[length];

        // Финализируем переменные для использования внутри лямбды
        final int fWidth = width;
        final int fHeight = height;
        final double fReMin = reMin, fReMax = reMax, fImMin = imMin, fImMax = imMax;
        final int fMaxIter = maxIter;

        IntStream stream = IntStream.range(0,length);
        if(isParallel) {
            stream = stream.parallel();
        }

        stream.forEach(i -> {
            int row = i / fWidth;
            int col = i % fWidth;

            // Линейная интерполяция: col/width пробегает [0,1)
            double cRe = fReMin + (fReMax - fReMin) * col / fWidth;
            double cIm = fImMin + (fImMax - fImMin) * row / fHeight;

            values[i] = smoothEscapeTime(cRe, cIm, fMaxIter);
        });

        // Превращаем массив оценок в перестановку индексов.
        return sortIndicesByValue(values,isParallel);
    }

    /**
     * Вычисляет сглаженное количество итераций (smooth iteration count).
     * <p>
     * Включает оптимизацию: если точка внутри главной кардиоиды или луковицы,
     * она точно принадлежит множеству, и мы сразу возвращаем {@code maxIter}.
     * </p>
     *
     * @param cRe Действительная часть точки {@code c}.
     * @param cIm Мнимая часть точки {@code c}.
     * @param maxIter Максимальное количество итераций.
     * @return Дробное значение escape-time.
     */
    private double smoothEscapeTime(double cRe, double cIm, int maxIter) {
        // === МАТЕМАТИЧЕСКАЯ ОПТИМИЗАЦИЯ (Cardioid/Bulb check) ===

        double xMinusQuarter = cRe - 0.25;
        double ySquared = cIm * cIm;
        double q = xMinusQuarter * xMinusQuarter + ySquared;

        // Проверка главной кардиоиды
        if (q * (q + xMinusQuarter) < 0.25 * ySquared) {
            return maxIter;
        }

        // Проверка луковицы (period-2 bulb)
        if ((cRe + 1.0) * (cRe + 1.0) + ySquared < 0.0625) {
            return maxIter;
        }

        double zRe = 0.0;
        double zIm = 0.0;
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

        // Smooth iteration count
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
     * <p>
     * Оптимизация: использует упаковку {@code double} в {@code long}
     * для быстрой примитивной сортировки без boxing.
     * </p>
     *
     * @param values Массив весов (escape-time).
     * @param isParallel Флаг параллельной сортировки.
     * @return Массив индексов.
     */
    private int[] sortIndicesByValue(double[] values,boolean isParallel) {
        int n = values.length;

        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            long bits = Double.doubleToLongBits(values[i]);
            // Инвертируем знаковый бит для корректной сортировки отрицательных
            if (bits < 0) bits ^= 0x7FFFFFFFFFFFFFFFL;
            // Старшие 32 бита — значение, младшие 32 бита — индекс
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
     * @return Строка "mandelbrot".
     */
    @Override
    public String getName() {
        return "mandelbrot";
    }
}