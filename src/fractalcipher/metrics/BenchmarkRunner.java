package fractalcipher.metrics;

import fractalcipher.domain.EncryptionMode;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.model.KeyDerivation;
import fractalcipher.model.diffusion.DiffusionSource;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.diffusion.NullDiffusion;
import fractalcipher.model.permutation.IdentityPermutation;
import fractalcipher.model.permutation.PermutationSource;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BenchmarkRunner {

    private static final long MASTER_SEED = 12345L;

    /**
     * СЕРИЯ 1: только перестановка.
     * Перебираем ВСЕ фракталы, но диффузия всегда null.
     * CBC выключен (PERMUTATION_ONLY).
     * Цель: сравнить фракталы по разрушению пространственных связей (корреляция).
     */
    public static List<BenchmarkResult> runPermutationOnly(PermutationSourceFactory pf,
                                                           Image originalImage) {
        // Создаём фабрику диффузии ТОЛЬКО с null — никаких logistic/henon
        DiffusionSourceFactory nullOnlyFactory = new DiffusionSourceFactory() {{
            // Очищаем всё, что зарегистрировано в конструкторе
            clear();
            register(new NullDiffusion());
        }};

        return run(
                pf.availableNames(),           // все фракталы
                nullOnlyFactory.availableNames(), // только "null"
                pf, nullOnlyFactory,
                originalImage,
                EncryptionMode.PERMUTATION_ONLY
        );
    }

    /**
     * СЕРИЯ 2: только диффузия.
     * Перебираем ВСЕ источники диффузии, но перестановка всегда identity.
     * CBC выключен (DIFFUSION_ONLY).
     * Цель: сравнить хаотические карты по NPCR/UACI/энтропии.
     */
    public static List<BenchmarkResult> runDiffusionOnly(DiffusionSourceFactory df,
                                                         Image originalImage) {
        // Создаём фабрику перестановок ТОЛЬКО с identity
        PermutationSourceFactory identityOnlyFactory = new PermutationSourceFactory() {{
            clear();
            register(new IdentityPermutation());
        }};

        return run(
                identityOnlyFactory.availableNames(), // только "identity"
                df.availableNames(),                  // все диффузии
                identityOnlyFactory, df,
                originalImage,
                EncryptionMode.DIFFUSION_ONLY
        );
    }

    /**
     * СЕРИЯ 3: полная схема.
     * Перебираем все комбинации перестановок и диффузий.
     * CBC включён (FULL).
     * Цель: оценить итоговую криптостойкость.
     */
    public static List<BenchmarkResult> runFull(PermutationSourceFactory pf,
                                                DiffusionSourceFactory df,
                                                Image originalImage) {
        return run(
                pf.availableNames(),
                df.availableNames(),
                pf, df,
                originalImage,
                EncryptionMode.FULL
        );
    }

    /**
     * Общий метод — принимает ЯВНЫЕ списки имён для перебора.
     * Это гарантирует, что каждая серия перебирает только то, что нужно.
     */
    private static List<BenchmarkResult> run(Iterable<String> permutationNames,
                                             Iterable<String> diffusionNames,
                                             PermutationSourceFactory permutationFactory,
                                             DiffusionSourceFactory diffusionFactory,
                                             Image originalImage,
                                             EncryptionMode mode) {
        List<BenchmarkResult> results = new ArrayList<>();
        Image modifiedImage = withOnePixelFlipped(originalImage);

        for (String permutationName : permutationNames) {
            for (String diffusionName : diffusionNames) {
                try {
                    long permSeed = KeyDerivation.deriveLong(
                            MASTER_SEED, "permutation:" + permutationName);
                    double x0 = KeyDerivation.deriveDouble(
                            MASTER_SEED, "diffusion:" + diffusionName + ":x0");
                    long sboxSeed = KeyDerivation.deriveLong(
                            MASTER_SEED, "diffusion:" + diffusionName + ":sbox");
                    long sboxCbc1 = KeyDerivation.deriveLong(
                            MASTER_SEED, "diffusion:" + diffusionName + ":cbc1");
                    long sboxCbc2 = KeyDerivation.deriveLong(
                            MASTER_SEED, "diffusion:" + diffusionName + ":cbc2");

                    Map<String, Double> permParams = new HashMap<>();
                    permParams.put("seed", (double) permSeed);

                    Map<String, Double> diffParams = new HashMap<>();
                    diffParams.put("x0", x0);
                    diffParams.put("sboxSeed", (double) sboxSeed);
                    diffParams.put("sboxCbc1", (double) sboxCbc1);
                    diffParams.put("sboxCbc2", (double) sboxCbc2);

                    long start = System.currentTimeMillis();

                    FractalChaosCipher cipher = new FractalChaosCipher(
                            "bench", permutationName + "+" + diffusionName,
                            permutationFactory.get(permutationName),
                            diffusionFactory.get(diffusionName),
                            permParams, diffParams, mode);
                    cipher.setImageInput(originalImage);
                    cipher.encrypt();
                    Image encrypted = cipher.getImageOutput();
                    long elapsed = System.currentTimeMillis() - start;

                    FractalChaosCipher cipherForModified = new FractalChaosCipher(
                            "bench", permutationName + "+" + diffusionName,
                            permutationFactory.get(permutationName),
                            diffusionFactory.get(diffusionName),
                            permParams, diffParams, mode);
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