package fractalcipher.model.permutation;

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

// Единственная НЕ-заглушка среди источников перестановки на момент
// написания. Идея: каждому пикселю (row, col) ставим в соответствие
// точку c на комплексной плоскости, считаем escape-time (сколько
// итераций z = z^2+c нужно, чтобы z "улетело" за границу), и это
// число используем как ключ сортировки — куда пиксель переедет.
public class MandelbrotPermutation implements PermutationSource {

    // Сколько максимум итераций делаем, прежде чем считать точку
    // "не убегающей" (то есть предположительно внутри множества).
    // Чем больше — тем точнее, но и медленнее.
    private static final int DEFAULT_MAX_ITERATIONS = 300;

    // Радиус, при превышении которого считаем, что z гарантированно
    // улетит в бесконечность (стандартное значение для Мандельброта — 2).
    private static final double ESCAPE_RADIUS = 2.0;

    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        // width обязателен в params — его подставляет FractalChaosCipher
        // перед вызовом. Если вдруг не передали, откатываемся к
        // length (то есть считаем изображение "одной строкой").
        int width = params.getOrDefault("width", (double) length).intValue();

        // height не передаём напрямую, а вычисляем как length/width,
        // чтобы не зависеть от двух источников правды.
        // Math.max(1, ...) — страховка от деления на 0, если width
        // почему-то оказался больше length.
        int height = Math.max(1, length / width);
        // === ИЗМЕНЕНИЕ 1: Детерминированный хаос из seed ===
        // Seed передаётся из FractalChaosCipher как часть ключа.
        // Если seed не передан — используем 0 (но это небезопасно,
        // поэтому в реальном приложении seed всегда должен быть).
        long seed = params.getOrDefault("seed",0.0).longValue();
        Random rnd = new Random(seed);

        // Генерируем параметры области детерминированно из seed.
        // Диапазоны подобраны так, чтобы захватывать интересные
        // участки множества Мандельброта (границу и окрестности).
        double reMin = params.getOrDefault("reMin", -2.0 - rnd.nextDouble() * 0.5);
        double reMax = params.getOrDefault("reMax", 1.0 + rnd.nextDouble() * 0.5);
        double imMin = params.getOrDefault("imMin", -1.5 - rnd.nextDouble() * 0.5);
        double imMax = params.getOrDefault("imMax", 1.5 + rnd.nextDouble() * 0.5);
        // Хаотическое максимальное число итераций
        int maxIter = params.getOrDefault("maxIter",
                (double) (DEFAULT_MAX_ITERATIONS + rnd.nextInt(700))).intValue();

        // Массив "оценок" по одной на пиксель — сюда положим escape-time.
        double[] values = new double[length];

        // Лямбда требует effectively final переменные
        final int fWidth = width;
        final int fHeight = height;
        final double fReMin = reMin, fReMax = reMax, fImMin = imMin, fImMax = imMax;
        final int fMaxIter = maxIter;

        IntStream.range(0,length).parallel().forEach(i -> {
            // Разворачиваем линейный индекс пикселя i (0..length-1)
            // обратно в двумерные координаты (row, col), как если бы
            // пиксели шли построчно слева направо, сверху вниз.
            int row = i / fWidth;
            int col = i % fWidth;

            // Линейная интерполяция: col пробегает 0..width-1, значит
            // col/width пробегает [0,1), и re линейно ложится в
            // [reMin, reMax). Аналогично для im по row/height.
            // c — координата пикселя
            double cRe = fReMin + (fReMax - fReMin) * col / fWidth;
            double cIm = fImMin + (fImMax - fImMin) * row / fHeight;

            // === ИЗМЕНЕНИЕ 2: Smooth iteration count ===
            values[i] = smoothEscapeTime(cRe, cIm, fMaxIter);
        });

        // Превращаем массив оценок в перестановку индексов.
        return sortIndicesByValue(values);
    }

    // === ИЗМЕНЕНИЕ 3: Smooth iteration count вместо целого ===
    // Возвращает дробное число итераций, что даёт непрерывное
    // значение и практически исключает повторения.
    private double smoothEscapeTime(double cRe, double cIm, int maxIter) {
        // === МАТЕМАТИЧЕСКАЯ ОПТИМИЗАЦИЯ (Cardioid/Bulb check) ===
        // Если точка внутри главной кардиоиды или луковицы — она точно в множестве.
        // Мы не тратим время на цикл, а сразу возвращаем maxIter.

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
        // ========================================================

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
            // (a+bi)^2 = (a^2 - b^2) + i(2ab)
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

    // Превращает массив значений values в перестановку индексов:
    // сортируем индексы 0..length-1 по тому, какое values[i] у них было,
    // и получившийся порядок индексов после сортировки — это и есть
    // permutation. Например, если values = [5.0, 1.0, 3.0], то индексы
    // после сортировки по значению — [1, 2, 0] (у индекса 1 самое
    // маленькое значение, у индекса 0 — самое большое).
    private int[] sortIndicesByValue(double[] values) {
        int n = values.length;

        // Упаковываем: старшие 32 бита — индекс, младшие — усечённое значение
        // (достаточно точности для сравнения, т.к. значения smooth iteration
        // не различаются на уровне 2^-32).
        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            // Превращаем double в сортируемый long через битовое представление
            long bits = Double.doubleToLongBits(values[i]);
            // Инвертируем знаковый бит для корректной сортировки отрицательных
            if (bits < 0) bits ^= 0x7FFFFFFFFFFFFFFFL;
            // Старшие 32 бита — значение, младшие — индекс
            packed[i] = (bits & 0xFFFFFFFF00000000L) | (i & 0xFFFFFFFFL);
        }

        java.util.Arrays.parallelSort(packed); // сортировка примитивов — в разы быстрее

        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int)(packed[i] & 0xFFFFFFFFL);
        }
        return result;
    }

    @Override
    public String getName() {
        return "Мандельброт";
    }
}