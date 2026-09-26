package fractalcipher.metrics;

import java.awt.image.BufferedImage;

/**
 * Стандартные метрики из статей по шифрованию изображений.
 * Реализованы по-настоящему — формулы общепринятые, ничего
 * не изобретали.
 */
public class ImageMetrics {

    /** Корреляция соседних по горизонтали пикселей (grayscale). Хороший шифр: около 0. */
    public static double correlationCoefficient(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int n = (width - 1) * height;
        double[] x = new double[n];
        double[] y = new double[n];

        int idx = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width - 1; col++) {
                x[idx] = grayscale(image.getRGB(col, row));
                y[idx] = grayscale(image.getRGB(col + 1, row));
                idx++;
            }
        }

        double meanX = mean(x);
        double meanY = mean(y);

        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            cov += (x[i] - meanX) * (y[i] - meanY);
            varX += (x[i] - meanX) * (x[i] - meanX);
            varY += (y[i] - meanY) * (y[i] - meanY);
        }
        return cov / Math.sqrt(varX * varY);
    }

    /** Энтропия Шеннона по яркости. Хороший шифр: около 8. */
    public static double entropy(BufferedImage image) {
        int[] histogram = new int[256];
        int width = image.getWidth();
        int height = image.getHeight();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                histogram[grayscale(image.getRGB(col, row))]++;
            }
        }

        double total = width * height;
        double entropy = 0;
        for (int count : histogram) {
            if (count == 0) continue;
            double p = count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /** NPCR — доля изменившихся пикселей между двумя шифротекстами. Хороший шифр: > 99.6%. */
    public static double npcr(BufferedImage image1, BufferedImage image2) {
        int width = image1.getWidth();
        int height = image1.getHeight();
        int changed = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (image1.getRGB(col, row) != image2.getRGB(col, row)) changed++;
            }
        }
        return 100.0 * changed / (width * height);
    }

    /** UACI — средняя нормированная разница интенсивности. Хороший шифр: около 33.4%. */
    public static double uaci(BufferedImage image1, BufferedImage image2) {
        int width = image1.getWidth();
        int height = image1.getHeight();
        double sum = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                sum += Math.abs(grayscale(image1.getRGB(col, row)) - grayscale(image2.getRGB(col, row)));
            }
        }
        return 100.0 * sum / (width * height * 255);
    }

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