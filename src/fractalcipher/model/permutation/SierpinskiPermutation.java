package fractalcipher.model.permutation;

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.Arrays;

public class SierpinskiPermutation implements PermutationSource {
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int width = params.getOrDefault("width", (double) length).intValue();
        int height = Math.max(1, length / width);
        long seed = params.getOrDefault("seed", 0.0).longValue();
        Random rnd = new Random(seed);
        boolean isParallel = params.getOrDefault("isParallel",0.0) == 1.0;

        // Случайный сдвиг для уникальности ключа
        int shiftX = rnd.nextInt(Math.max(1, width));
        int shiftY = rnd.nextInt(Math.max(1, height));

        double[] values = new double[length];
        final int fWidth = width, fHeight = height, fShiftX = shiftX, fShiftY = shiftY;

        IntStream stream = IntStream.range(1,length);
        if(isParallel) {
            stream = stream.parallel();
        }

        stream.forEach(i -> {
            int row = i / fWidth;
            int col = i % fWidth;

            // Сдвигаем координаты (чтобы перестановка зависела от ключа)
            int x = (col + fShiftX) % fWidth;
            int y = (row + fShiftY) % fHeight;

            // Вычисляем расстояние до ближайшей точки множества Серпинского.
            // Точки внутри множества подчиняются правилу: (x & y) == x.
            int common = x & y;
            int distX = x ^ common;
            int distY = y ^ common;

            // Микро-вариация для избежания коллизий
            values[i] = distX + distY + (i * 1e-9);
        });

        return sortIndicesByValue(values,isParallel);
    }

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
            // сортировка примитивов — в разы быстрее
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

    @Override
    public String getName() {
        return "sierphinski";
    }
}