package fractalcipher.metrics;

import java.awt.image.BufferedImage;

// Стандартные метрики качества шифрования изображений из статей по
// теме. Все формулы общепринятые (это не наше изобретение), поэтому
// реализованы по-настоящему, а не заглушками.
public class ImageMetrics {

    // Коэффициент корреляции Пирсона между соседними по горизонтали
    // пикселями. У обычной фотографии соседние пиксели почти всегда
    // похожи (небо рядом с небом и т.п.), поэтому корреляция близка
    // к 1. Хороший шифр должен эту связь полностью разрушить —
    // корреляция шифротекста должна быть близка к 0.
    public static double correlationCoefficient(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Пар "пиксель - его правый сосед" на каждой строке (width-1),
        // умноженное на число строк (height).
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
        return cov / Math.sqrt(varX * varY);
    }

    // Энтропия Шеннона по яркости: насколько равномерно распределены
    // значения яркости от 0 до 255. Максимум для 8-битного канала — 8
    // (когда все 256 значений встречаются одинаково часто). Хороший
    // шифр должен "размазать" яркости почти равномерно, поэтому
    // хорошее значение — близко к 8.
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

    private static double entropyFromHist(int[] hist, double total) {
        double entropy = 0;
        for (int count : hist) {
            if (count == 0) continue;
            double p = count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    // NPCR (Number of Pixels Change Rate) — какой процент пикселей
    // отличается между двумя шифротекстами. Проверяет чувствительность
    // шифра: если поменять всего 1 пиксель в исходном изображении и
    // зашифровать заново, хороший шифр должен изменить ПОЧТИ ВСЕ
    // пиксели результата (лавинный эффект). Хорошее значение: > 99.6%.
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

    // UACI (Unified Average Changing Intensity) — НАСКОЛЬКО СИЛЬНО в
    // среднем отличаются пиксели (а не просто "отличаются или нет",
    // как в NPCR). Дополняет NPCR: можно поменять все пиксели (NPCR=100%),
    // но на маленькую величину — UACI это покажет. Хорошее значение: ~33.4%.
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

    // Вспомогательная функция: упрощённая яркость пикселя как среднее
    // R, G и B (без весов по восприятию глаза — для метрик сравнения
    // такой простой яркости достаточно).
    private static int grayscale(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r + g + b) / 3;
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
}