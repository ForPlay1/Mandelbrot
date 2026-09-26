package fractalcipher.metrics;

import fractalcipher.model.EncryptionKey;
import fractalcipher.model.ImageCipher;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Перебирает все комбинации "источник перестановки x источник диффузии",
 * шифрует тестовое изображение и считает метрики. Пары, где хотя бы
 * один источник ещё не реализован (заглушка), просто пропускаются
 * с пометкой в консоли — так можно подключать кандидатов по мере
 * готовности, не ломая сравнение уже готовых.
 *
 * NPCR/UACI считаются по стандартной методике: то же изображение
 * с одним изменённым пикселем шифруется тем же ключом, и сравниваются
 * два шифротекста.
 */
public class BenchmarkRunner {

    public static List<BenchmarkResult> run(ImageCipher cipher, BufferedImage originalImage,
                                            List<String> permutationNames, List<String> diffusionNames) {
        List<BenchmarkResult> results = new ArrayList<>();
        BufferedImage modifiedImage = withOnePixelFlipped(originalImage);

        for (String permutationName : permutationNames) {
            for (String diffusionName : diffusionNames) {
                EncryptionKey key = new EncryptionKey(
                        permutationName, new HashMap<>(),
                        diffusionName, new HashMap<>()
                );

                try {
                    long start = System.currentTimeMillis();
                    BufferedImage encrypted = cipher.encrypt(originalImage, key);
                    long elapsed = System.currentTimeMillis() - start;

                    BufferedImage encryptedModified = cipher.encrypt(modifiedImage, key);

                    results.add(new BenchmarkResult(
                            permutationName, diffusionName, elapsed,
                            ImageMetrics.correlationCoefficient(encrypted),
                            ImageMetrics.entropy(encrypted),
                            ImageMetrics.npcr(encrypted, encryptedModified),
                            ImageMetrics.uaci(encrypted, encryptedModified)
                    ));
                } catch (UnsupportedOperationException e) {
                    System.out.println("[Пропущено] " + permutationName + " + " + diffusionName + ": " + e.getMessage());
                }
            }
        }
        return results;
    }

    private static BufferedImage withOnePixelFlipped(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, width, height, image.getRGB(0, 0, width, height, null, 0, width), 0, width);

        int pixel = copy.getRGB(0, 0);
        copy.setRGB(0, 0, pixel ^ 0x00000001); // меняем младший бит одного пикселя
        return copy;
    }
}