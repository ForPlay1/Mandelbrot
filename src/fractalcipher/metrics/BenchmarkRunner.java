package fractalcipher.metrics;

import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.model.KeyDerivation;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BenchmarkRunner {

    // Мастер-seed для бенчмарка. Фиксирован, чтобы результаты
    // были воспроизводимы между запусками.
    private static final long MASTER_SEED = 12345L;

    public static List<BenchmarkResult> run(PermutationSourceFactory permutationFactory,
                                            DiffusionSourceFactory diffusionFactory,
                                            Image originalImage) {
        List<BenchmarkResult> results = new ArrayList<>();
        Image modifiedImage = withOnePixelFlipped(originalImage);

        for (String permutationName : permutationFactory.availableNames()) {
            for (String diffusionName : diffusionFactory.availableNames()) {
                try {
                    // === Деривация параметров из мастер-seed ===
                    long permSeed = KeyDerivation.deriveLong(
                            MASTER_SEED, "permutation:" + permutationName);
                    double x0 = KeyDerivation.deriveDouble(
                            MASTER_SEED, "diffusion:" + diffusionName + ":x0");
                    long sboxSeed = KeyDerivation.deriveLong(
                            MASTER_SEED, "diffusion:" + diffusionName + ":sbox");

// === НОВОЕ: S-боксы для CBC внутри FractalChaosCipher ===
// Домены разные, чтобы S-боксы не совпали с sboxSeed (тот идёт в LogisticMapDiffusion).
                    long sboxCbc1 = KeyDerivation.deriveLong(
                            MASTER_SEED, "diffusion:" + diffusionName + ":cbc1");
                    long sboxCbc2 = KeyDerivation.deriveLong(
                            MASTER_SEED, "diffusion:" + diffusionName + ":cbc2");

                    Map<String, Double> permParams = new HashMap<>();
                    permParams.put("seed", (double) permSeed);

                    Map<String, Double> diffParams = new HashMap<>();
                    diffParams.put("x0", x0);
                    diffParams.put("sboxSeed", (double) sboxSeed);
                    // === НОВОЕ ===
                    diffParams.put("sboxCbc1", (double) sboxCbc1);
                    diffParams.put("sboxCbc2", (double) sboxCbc2);

                    // === Шифрование ОРИГИНАЛА ===
                    long start = System.currentTimeMillis();

                    FractalChaosCipher cipher = new FractalChaosCipher(
                            "bench", permutationName + "+" + diffusionName,
                            permutationFactory.get(permutationName),
                            diffusionFactory.get(diffusionName),
                            permParams, diffParams);
                    cipher.setImageInput(originalImage);
                    cipher.encrypt();
                    Image encrypted = cipher.getImageOutput();
                    long elapsed = System.currentTimeMillis() - start;

                    // === Шифрование ИЗМЕНЁННОГО (тот же ключ!) ===
                    FractalChaosCipher cipherForModified = new FractalChaosCipher(
                            "bench", permutationName + "+" + diffusionName,
                            permutationFactory.get(permutationName),
                            diffusionFactory.get(diffusionName),
                            permParams, diffParams);  // ТЕ ЖЕ ПАРАМЕТРЫ
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
        copy.setRGB(0, 0, pixel ^ 0x00000001);  // инверсия младшего бита синего канала

        return new Image(image.getId() + "_flip", image.getName(), copy, image.getSize(), image.getFileType());
    }
}