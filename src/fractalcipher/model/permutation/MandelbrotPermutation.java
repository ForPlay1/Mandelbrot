package fractalcipher.model.permutation;

import fractalcipher.model.Complex;

import java.util.Arrays;
import java.util.Map;

// Единственная НЕ-заглушка среди источников перестановки на момент
// написания. Идея: каждому пикселю (row, col) ставим в соответствие
// точку c на комплексной плоскости, считаем escape-time (сколько
// итераций z = z^2+c нужно, чтобы z "улетело" за границу), и это
// число используем как ключ сортировки — куда пиксель переедет.
public class MandelbrotPermutation implements PermutationSource {

    // Сколько максимум итераций делаем, прежде чем считать точку
    // "не убегающей" (то есть предположительно внутри множества).
    // Чем больше — тем точнее, но и медленнее.
    private static final int MAX_ITERATIONS = 100;

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

        // Границы области комплексной плоскости, которую разворачиваем
        // на изображение. Стандартный "обзорный" прямоугольник
        // множества Мандельброта — можно переопределить через params.
        double reMin = params.getOrDefault("reMin", -2.0);
        double reMax = params.getOrDefault("reMax", 1.0);
        double imMin = params.getOrDefault("imMin", -1.5);
        double imMax = params.getOrDefault("imMax", 1.5);

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

            // Считаем "оценку" именно этого пикселя.
            values[i] = escapeTime(new Complex(re, im));
        }

        // Превращаем массив оценок в перестановку индексов.
        return sortIndicesByValue(values);
    }

    // Стандартный алгоритм escape-time для множества Мандельброта:
    // начинаем с z=0, повторяем z = z^2 + c, пока |z| не превысит
    // ESCAPE_RADIUS или не кончится лимит итераций. Возвращаем
    // число сделанных итераций — чем быстрее "улетело", тем меньше число.
    private int escapeTime(Complex c) {
        Complex z = new Complex(0, 0); // стартуем с нуля, как принято для Мандельброта
        int iterations = 0;
        double radiusSquared = ESCAPE_RADIUS * ESCAPE_RADIUS; // сравниваем квадраты, чтобы не считать sqrt

        while (iterations < MAX_ITERATIONS && z.absSquared() <= radiusSquared) {
            z = z.multiply(z).add(c); // собственно итерация z = z^2 + c
            iterations++;
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
        // Используем Integer[], а не int[], потому что Arrays.sort
        // с компаратором работает только с объектами, не с примитивами.
        Integer[] indices = new Integer[values.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;

        // Сортируем индексы, сравнивая значения values[a] и values[b],
        // на которые эти индексы указывают (а не сами индексы).
        // Arrays.sort для объектов — устойчивая сортировка (TimSort),
        // так что порядок пикселей с одинаковым escape-time не будет
        // хаотично меняться между запусками.
        Arrays.sort(indices, (a, b) -> Double.compare(values[a], values[b]));

        // Конвертируем обратно в примитивный int[], потому что
        // весь остальной код (FractalChaosCipher) работает с int[].
        int[] result = new int[values.length];
        for (int i = 0; i < result.length; i++) result[i] = indices[i];
        return result;
    }

    @Override
    public String getName() {
        return "mandelbrot";
    }
}