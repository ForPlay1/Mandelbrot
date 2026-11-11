package fractalcipher.model.permutation;

import java.util.Random;
import java.util.Map;
import java.util.Arrays;

/**
 * Перестановка на основе канторовой диагональной матрицы.
 * <p>
 * Идея: функция спаривания Кантора {@code C(x, y) = (x+y)(x+y+1)/2 + y}
 * задаёт биекцию {@code N² → N}. Мы используем её как "вес" пикселя:
 * чем больше {@code C(row, col)}, тем позже пиксель в перестановке.
 * </p>
 * <p>
 * Чтобы перестановка зависела от ключа, строки и столбцы предварительно
 * сдвигаются на хаотические величины, полученные из {@code seed}.
 * Это аналог "ordered rotation scrambling" из литературы по канторовой перестановке.
 * </p>
 */
public class CantorPermutation implements PermutationSource {
    /**
     * Генерирует перестановку на основе канторовой диагональной матрицы.
     *
     * @param length Количество пикселей.
     * @param params Параметры (width, seed, isParallel).
     * @return Массив перестановок индексов.
     */
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int width = params.getOrDefault("width",(double)length).intValue();
        int height = Math.max(1,length/width);
        long seed = params.getOrDefault("seed",0.0).longValue();
        boolean isParallel = params.getOrDefault("isParallel",0.0) == 1.0;
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

            // Сдвигаем индексы по кругу для хаотического порядка обхода
            int shiftedRow = (row + rowShift) % height;
            int shiftedCol = (col + colShift) % width;

            // Функция спаривания Кантора
            long sum = shiftedRow + shiftedCol;
            long cantor = (sum * (sum + 1)) / 2 + shiftedCol;

            // Микро-вариация для избежания коллизий
            weights[i] = cantor + (i * 1e-9);
        }

        // Сортируем индексы по весам — тот же примитивный приём,
        // что и в MandelbrotPermutation.
        return sortIndicesByValue(weights,isParallel);
    }

    /**
     * Примитивная сортировка через {@code long[]} без boxing.
     *
     * @param values Массив весов.
     * @param isParallel Флаг параллельной сортировки.
     * @return Массив индексов.
     */
    private int[] sortIndicesByValue(double[] values,boolean isParallel) {
        int n = values.length;

        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            long bits = Double.doubleToLongBits(values[i]);
            if (bits < 0) bits ^= 0x7FFFFFFFFFFFFFFFL;
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
     * @return Строка "cantor".
     */
    @Override
    public String getName() {
        return "cantor";
    }
}