package fractalcipher.metrics;

import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Перебирает ВСЕ пары (источник перестановки x источник диффузии)
 * из фабрик и для каждой пары создаёт СВОЙ экземпляр FractalChaosCipher
 * "на лету" — никакого реестра классов, добавление нового фрактала
 * или карты в *SourceFactory автоматически включает его в сравнение.
 */
public class BenchmarkRunner {

    public static List<BenchmarkResult> run(PermutationSourceFactory permutationFactory,
                                            DiffusionSourceFactory diffusionFactory,
                                            Image originalImage) {
        List<BenchmarkResult> results = new ArrayList<>();
        Image modifiedImage = withOnePixelFlipped(originalImage);

        for (String permutationName : permutationFactory.availableNames()) {
            for (String diffusionName : diffusionFactory.availableNames()) {
                try {
                    long start = System.currentTimeMillis();

                    FractalChaosCipher cipher = new FractalChaosCipher(
                            "bench", permutationName + "+" + diffusionName,
                            permutationFactory.get(permutationName),
                            diffusionFactory.get(diffusionName)
                    );
                    cipher.setImageInput(originalImage);
                    cipher.encrypt();
                    Image encrypted = cipher.getImageOutput();
                    long elapsed = System.currentTimeMillis() - start;

                    // Свежий экземпляр для изменённого изображения — Cipher хранит
                    // состояние (imageInput/imageOutput), лучше не переиспользовать.
                    FractalChaosCipher cipherForModified = new FractalChaosCipher(
                            "bench", permutationName + "+" + diffusionName,
                            permutationFactory.get(permutationName),
                            diffusionFactory.get(diffusionName)
                    );
                    cipherForModified.setImageInput(modifiedImage);
                    cipherForModified.encrypt();
                    Image encryptedModified = cipherForModified.getImageOutput();

                    results.add(new BenchmarkResult(
                            permutationName, diffusionName, elapsed,
                            ImageMetrics.correlationCoefficient(encrypted.getImage()),
                            ImageMetrics.entropy(encrypted.getImage()),
                            ImageMetrics.npcr(encrypted.getImage(), encryptedModified.getImage()),
                            ImageMetrics.uaci(encrypted.getImage(), encryptedModified.getImage())
                    ));
                } catch (UnsupportedOperationException e) {
                    System.out.println("[Пропущено] " + permutationName + " + " + diffusionName + ": " + e.getMessage());
                }
            }
        }
        return results;
    }

    private static Image withOnePixelFlipped(Image image) {
        BufferedImage buffered = image.getImage();
        int width = buffered.getWidth();
        int height = buffered.getHeight();

        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, width, height, buffered.getRGB(0, 0, width, height, null, 0, width), 0, width);

        int pixel = copy.getRGB(0, 0);
        copy.setRGB(0, 0, pixel ^ 0x00000001);

        return new Image(image.getId() + "_flip", image.getName(), copy, image.getSize(), image.getFileType());
    }
}