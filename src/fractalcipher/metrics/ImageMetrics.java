package fractalcipher.metrics;

import java.awt.image.BufferedImage;

/**
 * Стандартные метрики качества шифрования изображений.
 * <p>
 * Все формулы — общепринятые в литературе по теме (это не изобретение
 * проекта), поэтому реализованы по-настоящему, а не заглушками.
 * Ссылки на определения есть в Javadoc каждого метода.
 * </p>
 * <p>
 * Все методы — статические, работают с {@link BufferedImage} и не
 * изменяют его. Пара «идеальных значений» для 8-битного канала:
 * </p>
 * <table border="1">
 *   <caption>Ориентиры метрик</caption>
 *   <tr><th>Метрика</th><th>Идеал</th><th>Смысл</th></tr>
 *   <tr><td>Correlation</td><td>≈ 0</td><td>соседние пиксели независимы</td></tr>
 *   <tr><td>Entropy</td><td>≈ 8</td><td>равномерное распределение яркостей</td></tr>
 *   <tr><td>NPCR</td><td>&gt; 99.6%</td><td>почти все пиксели меняются при изменении 1 бита</td></tr>
 *   <tr><td>UACI</td><td>≈ 33.4%</td><td>средняя сила изменения пикселей</td></tr>
 *   <tr><td>MSE</td><td>0</td><td>идеальное обратимое восстановление</td></tr>
 * </table>
 *
 * @author FractalCipher Team
 * @see BenchmarkResult
 */
public class ImageMetrics {

    /** Утилитарный класс — инстанцирование не нужно. */
    private ImageMetrics() {}


    /**
     * Коэффициент корреляции Пирсона между соседними по горизонтали
     * пикселями.
     * <p>
     * У обычной фотографии соседние пиксели почти всегда похожи
     * (небо рядом с небом и т.п.), поэтому корреляция близка к 1.
     * Хороший шифр должен эту связь полностью разрушить — корреляция
     * шифротекста должна быть близка к 0.
     * </p>
     * <p>
     * Яркость пикселя считается как среднее R, G, B (см. {@link #grayscale}).
     * Если одна из переменных вырождена (однотонное изображение) —
     * возвращается {@code 0.0}, чтобы не получить {@code NaN}.
     * </p>
     *
     * @param image изображение (обычно — шифротекст)
     * @return коэффициент корреляции в диапазоне [−1, 1]; 0 — идеал
     */
    public static double correlationCoefficient(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Пар «пиксель — правый сосед»: (width − 1) на каждой строке
        int n = (width - 1) * height;
        double[] x = new double[n]; // яркости "левых" пикселей пары
        double[] y = new double[n]; // яркости "правых" пикселей пары

        int idx = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width - 1; col++) {
                x[idx] = grayscale(image.getRGB(col, row));
                y[idx] = grayscale(image.getRGB(col + 1, row)); // сосед справа
                idx++;
            }
        }

        double meanX = mean(x);
        double meanY = mean(y);

        // Классическая формула корреляции Пирсона:
        // cov(X,Y) / sqrt(var(X) * var(Y))
        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            cov += (x[i] - meanX) * (y[i] - meanY);
            varX += (x[i] - meanX) * (x[i] - meanX);
            varY += (y[i] - meanY) * (y[i] - meanY);
        }

        // Защита от деления на ноль для однотонных изображений:
        // без неё получили бы NaN, что ломает сравнение в бенчмарке
        if (varX == 0 || varY == 0) return 0.0;

        return cov / Math.sqrt(varX * varY);
    }

    /**
     * Энтропия Шеннона по яркости.
     * <p>
     * Показывает, насколько равномерно распределены значения яркости
     * от 0 до 255. Максимум для 8-битного канала — 8 (когда все 256
     * значений встречаются одинаково часто). Хороший шифр «размазывает»
     * яркости почти равномерно, поэтому хорошее значение — близко к 8.
     * </p>
     * <p>
     * Считается как среднее энтропий трёх каналов R, G, B.
     * </p>
     *
     * @param image изображение (обычно — шифротекст)
     * @return энтропия в битах на канал, от 0 до 8; 8 — идеал
     */
    public static double entropy(BufferedImage image) {
        int[] histR = new int[256], histG = new int[256], histB = new int[256];
        int width = image.getWidth(), height = image.getHeight();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int argb = image.getRGB(col, row);
                histR[(argb >> 16) & 0xFF]++;
                histG[(argb >> 8) & 0xFF]++;
                histB[argb & 0xFF]++;
            }
        }
        double total = (double) width * height;
        return (entropyFromHist(histR, total) + entropyFromHist(histG, total) + entropyFromHist(histB, total)) / 3.0;
    }

    /**
     * Энтропия Шеннона по одной гистограмме.
     *
     * @param hist  частоты значений 0..255
     * @param total общее число пикселей
     * @return энтропия в битах, от 0 до 8
     */
    private static double entropyFromHist(int[] hist, double total) {
        double entropy = 0;
        for (int count : hist) {
            if (count == 0) continue;
            double p = count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /**
     * NPCR (Number of Pixels Change Rate) — какой процент пикселей
     * отличается между двумя шифротекстами.
     * <p>
     * Проверяет чувствительность шифра: если поменять всего 1 пиксель
     * в исходном изображении и зашифровать заново, хороший шифр должен
     * изменить ПОЧТИ ВСЕ пиксели результата (лавинный эффект).
     * Хорошее значение — более 99.6%.
     * </p>
     * <p>
     * Сравнивается весь ARGB-{@code int} целиком, а не только яркость:
     * по определению NPCR пиксель считается изменившимся при <b>любом</b>
     * отличии значения.
     * </p>
     *
     * @param image1 первый шифротекст
     * @param image2 второй шифротекст (получен из изменённого на 1 пиксель оригинала)
     * @return процент изменившихся пикселей, 0..100
     */
    public static double npcr(BufferedImage image1, BufferedImage image2) {
        int width = image1.getWidth();
        int height = image1.getHeight();
        int changed = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                // Сравниваем весь ARGB int целиком, а не только яркость —
                // NPCR по определению считает пиксель разным при ЛЮБОМ
                // отличии значения.
                if (image1.getRGB(col, row) != image2.getRGB(col, row)) changed++;
            }
        }
        return 100.0 * changed / (width * height);
    }

    /**
     * UACI (Unified Average Changing Intensity) — насколько <b>сильно</b>
     * в среднем отличаются пиксели (в отличие от NPCR, который считает
     * «отличается / нет»).
     * <p>
     * Дополняет NPCR: можно поменять все пиксели (NPCR = 100%), но на
     * маленькую величину — UACI это покажет. Хорошее значение ≈ 33.4%.
     * </p>
     * <p>
     * Считается как средняя абсолютная разность по каналам R, G, B,
     * нормированная на 255.
     * </p>
     *
     * @param image1 первый шифротекст
     * @param image2 второй шифротекст
     * @return средняя интенсивность изменения, 0..100
     */
    public static double uaci(BufferedImage image1, BufferedImage image2) {
        int width = image1.getWidth();
        int height = image1.getHeight();
        double sumR = 0, sumG = 0, sumB = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int p1 = image1.getRGB(col, row);
                int p2 = image2.getRGB(col, row);

                int r1 = (p1 >> 16) & 0xFF, r2 = (p2 >> 16) & 0xFF;
                int g1 = (p1 >> 8)  & 0xFF, g2 = (p2 >> 8)  & 0xFF;
                int b1 =  p1        & 0xFF, b2 =  p2        & 0xFF;

                sumR += Math.abs(r1 - r2);
                sumG += Math.abs(g1 - g2);
                sumB += Math.abs(b1 - b2);
            }
        }
        double n = (double) width * height;
        return 100.0 * (sumR + sumG + sumB) / (3.0 * n * 255.0);
    }

    /**
     * MSE (Mean Squared Error) — среднеквадратичная ошибка между
     * оригиналом и изображением после расшифровки.
     * <p>
     * Если MSE = 0, изображение восстановлено идеально (без потерь).
     * Любое ненулевое значение означает, что шифрование/расшифровка
     * внесли искажения — это баг, а не «почти хорошо»: шифр обязан
     * быть обратимым побитово.
     * </p>
     * <p>
     * Суммируются квадраты разностей по каналам R, G, B, затем
     * нормируется на {@code width · height · 3}.
     * </p>
     *
     * @param original  оригинальное изображение
     * @param decrypted изображение после расшифровки
     * @return MSE ≥ 0; 0 — идеал
     */
    public static double mse(BufferedImage original, BufferedImage decrypted) {
        int width = original.getWidth();
        int height = original.getHeight();
        double sum = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int p1 = original.getRGB(col, row);
                int p2 = decrypted.getRGB(col, row);

                int r1 = (p1 >> 16) & 0xFF, r2 = (p2 >> 16) & 0xFF;
                int g1 = (p1 >> 8)  & 0xFF, g2 = (p2 >> 8)  & 0xFF;
                int b1 =  p1        & 0xFF, b2 =  p2        & 0xFF;

                // Суммируем квадраты разностей по трем каналам (R, G, B)
                sum += (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2);
            }
        }

        // Нормируем на общее количество пикселей и на 3 канала
        return sum / (width * height * 3.0);
    }

    /**
     * Упрощённая яркость пикселя — среднее арифметическое R, G, B.
     * <p>
     * Без весов по восприятию глаза: для метрик сравнения такой
     * простой яркости достаточно, а лишняя формула усложнила бы код
     * без выигрыша в точности.
     * </p>
     *
     * @param argb ARGB-значение пикселя
     * @return яркость 0..255
     */
    private static int grayscale(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r + g + b) / 3;
    }

    /**
     * Среднее арифметическое массива.
     *
     * @param values массив (не пустой)
     * @return среднее
     */
    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
}