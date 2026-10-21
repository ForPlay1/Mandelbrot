package fractalcipher.model.permutation;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;

public class DragonPermutation implements PermutationSource {
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int width = params.getOrDefault("width", (double) length).intValue();
        int height = Math.max(1, length / width);
        long seed = params.getOrDefault("seed", 0.0).longValue();
        Random rnd = new Random(seed);

        // Генерируем точки кривой Дракона (2^16 = 65536 точек)
        int maxIter = 16;
        int pathLen = 1 << maxIter;
        int[] px = new int[pathLen];
        int[] py = new int[pathLen];
        int x = 0, y = 0, dir = 0;
        px[0] = 0; py[0] = 0;

        for (int i = 1; i < pathLen; i++) {
            int turn = ((i & -i) << 1) & i;
            if (turn != 0) dir = (dir + 1) % 4; // Налево
            else dir = (dir + 3) % 4;           // Направо

            if (dir == 0) x++;
            else if (dir == 1) y++;
            else if (dir == 2) x--;
            else y--;

            px[i] = x;
            py[i] = y;
        }

        // Находим границы кривой
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < pathLen; i++) {
            if (px[i] < minX) minX = px[i];
            if (px[i] > maxX) maxX = px[i];
            if (py[i] < minY) minY = py[i];
            if (py[i] > maxY) maxY = py[i];
        }

        // Масштабируем под размеры картинки
        double scaleX = (double)(width - 1) / Math.max(1, maxX - minX);
        double scaleY = (double)(height - 1) / Math.max(1, maxY - minY);

        int shiftX = rnd.nextInt(Math.max(1, width));
        int shiftY = rnd.nextInt(Math.max(1, height));

        // Создаем карту расстояний до кривой
        double[] grid = new double[length];
        Arrays.fill(grid, Double.MAX_VALUE);

        // Отмечаем точки кривой на сетке
        for (int j = 0; j < pathLen; j++) {
            int rx = (int)((px[j] - minX) * scaleX);
            int ry = (int)((py[j] - minY) * scaleY);

            rx = (rx + shiftX) % width;
            ry = (ry + shiftY) % height;

            if (rx >= 0 && rx < width && ry >= 0 && ry < height) {
                grid[ry * width + rx] = 0.0;
            }
        }

        // Двухпроходный алгоритм Distance Transform (поиск расстояния)
        // Прямой проход
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int i = r * width + c;
                if (r > 0) grid[i] = Math.min(grid[i], grid[i - width] + 1);
                if (c > 0) grid[i] = Math.min(grid[i], grid[i - 1] + 1);
            }
        }
        // Обратный проход
        for (int r = height - 1; r >= 0; r--) {
            for (int c = width - 1; c >= 0; c--) {
                int i = r * width + c;
                if (r < height - 1) grid[i] = Math.min(grid[i], grid[i + width] + 1);
                if (c < width - 1) grid[i] = Math.min(grid[i], grid[i + 1] + 1);
            }
        }

        double[] values = new double[length];
        for (int i = 0; i < length; i++) {
            values[i] = grid[i] + (i * 1e-9); // Микро-вариация
        }

        return sortIndicesByValue(values);
    }

    private int[] sortIndicesByValue(double[] values) {
        int n = values.length;
        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            long bits = Double.doubleToLongBits(values[i]);
            if (bits < 0) bits ^= 0x7FFFFFFFFFFFFFFFL;
            packed[i] = (bits & 0xFFFFFFFF00000000L) | (i & 0xFFFFFFFFL);
        }
        Arrays.parallelSort(packed);
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int)(packed[i] & 0xFFFFFFFFL);
        }
        return result;
    }

    @Override
    public String getName() {
        return "Дракон";
    }
}