package fractalcipher.model.permutation;

import fractalcipher.model.Complex;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;

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

        for (int i = 0; i < length; i++) {
            // Разворачиваем линейный индекс пикселя i (0..length-1)
            // обратно в двумерные координаты (row, col), как если бы
            // пиксели шли построчно слева направо, сверху вниз.
            int row = i / width;
            int col = i % width;

            // Линейная интерполяция: col пробегает 0..width-1, значит
            // col/width пробегает [0,1), и re линейно ложится в
            // [reMin, reMax). Аналогично для im по row/height.
            double re = reMin + (reMax - reMin) * col / width;
            double im = imMin + (imMax - imMin) * row / height;

            // === ИЗМЕНЕНИЕ 2: Smooth iteration count ===
            values[i] = smoothEscapeTime(new Complex(re, im), maxIter);
        }

        // Превращаем массив оценок в перестановку индексов.
        return sortIndicesByValue(values);
    }

    // === ИЗМЕНЕНИЕ 3: Smooth iteration count вместо целого ===
    // Возвращает дробное число итераций, что даёт непрерывное
    // значение и практически исключает повторения.
    private double smoothEscapeTime(Complex c, int maxIter) {
        Complex z = new Complex(0, 0);
        int iterations = 0;
        double radiusSquared = ESCAPE_RADIUS * ESCAPE_RADIUS;

        while (iterations < maxIter && z.absSquared() <= radiusSquared) {
            z = z.multiply(z).add(c);
            iterations++;
        }

        // Если точка "улетела" — вычисляем smooth iteration count.
        // Формула: nu = log(log(|z|) / log(2)) / log(2)
        // smoothIter = iterations + 1 - nu
        if (iterations < maxIter) {
            double absZ = Math.sqrt(z.absSquared());
            // Защита от log(0) и отрицательных значений
            if (absZ > 1.0) {
                double logZn = Math.log(absZ);
                double nu = Math.log(logZn / Math.log(2.0)) / Math.log(2.0);
                return iterations + 1.0 - nu;
            }
        }

        // Точка внутри множества — возвращаем maxIter (или близкое значение)
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

        java.util.Arrays.sort(packed); // сортировка примитивов — в разы быстрее

        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int)(packed[i] & 0xFFFFFFFFL);
        }
        return result;
    }

    @Override
    public String getName() {
        return "mandelbrot";
    }
}