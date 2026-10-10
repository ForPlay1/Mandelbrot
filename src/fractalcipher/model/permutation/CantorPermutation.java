package fractalcipher.model.permutation;

import java.util.Random;
import java.util.Map;
import java.util.Arrays;

/**
 * Перестановка на основе канторовой диагональной матрицы.
 *
 * Идея: функция спаривания Кантора C(x, y) = (x+y)(x+y+1)/2 + y
 * задаёт биекцию N² → N. Мы используем её как "вес" пикселя:
 * чем больше C(row, col), тем позже пиксель в перестановке.
 *
 * Чтобы перестановка зависела от ключа, строки и столбцы
 * предварительно сдвигаются на хаотические величины, полученные
 * из seed. Это аналог "ordered rotation scrambling" из литературы
 * по канторовой диагональной перестановке [citation:4][citation:14].
 */
public class CantorPermutation implements PermutationSource {

    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int width = params.getOrDefault("width",(double)length).intValue();
        int height = Math.max(1,length/width);

        long seed = params.getOrDefault("seed",0.0).longValue();
        Random rnd = new Random(seed);

        // Хаотические сдвиги для строк и столбцов.
        // Это делает перестановку зависимой от ключа.
        int rowShift = rnd.nextInt(Math.max(1, height));
        int colShift = rnd.nextInt(Math.max(1, width));

        // Массив "весов" — по одному на пиксель.
        double[] weights = new double[length];

        for(int i=0;i<length;i++){
            int row = i / width;
            int col = i % width;

            // Сдвигаем индексы по кругу, чтобы получить хаотический
            // порядок обхода. Это ключевая идея "ordered rotation scrambling"
            int shiftedRow = (row + rowShift) % height;
            int shiftedCol = (col + colShift) % width;

            // Вычисляем функцию спаривания Кантора.
            // Используем long, чтобы избежать переполнения:
            // для 1024×1024 (x+y) может достигать 2046,
            // (x+y)² ≈ 4.2 млн — помещается в long.
            long sum = shiftedRow + shiftedCol;
            long cantor = (sum * (sum + 1)) / 2 + shiftedCol;

            // Приводим к double и добавляем микро-вариацию,
            // чтобы избежать одинаковых значений (маловероятно,
            // но возможно при больших изображениях).
            weights[i] = cantor + (i * 1e-9);
        }

        // Сортируем индексы по весам — тот же примитивный приём,
        // что и в MandelbrotPermutation.
        return sortIndicesByValue(weights);
    }

    /**
     * Примитивная сортировка через long[] — без boxing.
     * Тот же приём, что в MandelbrotPermutation и JuliaPermutation.
     */
    private int[] sortIndicesByValue(double[] values) {
        int n = values.length;

        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            long bits = Double.doubleToLongBits(values[i]);
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
        return "cantor";
    }
}