package fractalcipher.model.permutation;

import fractalcipher.model.Complex;

import java.util.Arrays;
import java.util.Map;

/**
 * Реализована по-настоящему (не заглушка): для каждого пикселя
 * (row, col) берём точку c в области множества Мандельброта,
 * считаем escape-time, затем сортируем индексы по этому значению —
 * это и есть перестановка. width/height обязаны быть в params
 * (их подставляет ImageCipherImpl).
 */
public class MandelbrotPermutation implements PermutationSource {

    private static final int MAX_ITERATIONS = 100;
    private static final double ESCAPE_RADIUS = 2.0;

    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int width = params.getOrDefault("width", (double) length).intValue();
        int height = Math.max(1, length / width);

        double reMin = params.getOrDefault("reMin", -2.0);
        double reMax = params.getOrDefault("reMax", 1.0);
        double imMin = params.getOrDefault("imMin", -1.5);
        double imMax = params.getOrDefault("imMax", 1.5);

        double[] values = new double[length];
        for (int i = 0; i < length; i++) {
            int row = i / width;
            int col = i % width;
            double re = reMin + (reMax - reMin) * col / width;
            double im = imMin + (imMax - imMin) * row / height;
            values[i] = escapeTime(new Complex(re, im));
        }

        return sortIndicesByValue(values);
    }

    private int escapeTime(Complex c) {
        Complex z = new Complex(0, 0);
        int iterations = 0;
        double radiusSquared = ESCAPE_RADIUS * ESCAPE_RADIUS;
        while (iterations < MAX_ITERATIONS && z.absSquared() <= radiusSquared) {
            z = z.multiply(z).add(c);
            iterations++;
        }
        return iterations;
    }

    /** Индексы 0..length-1, отсортированные по values[i] (устойчивая сортировка). */
    private int[] sortIndicesByValue(double[] values) {
        Integer[] indices = new Integer[values.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(values[a], values[b]));

        int[] result = new int[values.length];
        for (int i = 0; i < result.length; i++) result[i] = indices[i];
        return result;
    }

    @Override
    public String getName() {
        return "mandelbrot";
    }
}