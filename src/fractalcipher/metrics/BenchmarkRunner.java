package fractalcipher.metrics;

import fractalcipher.domain.EncryptionMode;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.model.KeyDerivation;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.diffusion.NullDiffusion;
import fractalcipher.model.permutation.IdentityPermutation;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Запускает три исследовательские серии бенчмарка для одного изображения.
 * <p>
 * <b>Проверка стабильности:</b> методы принимают мастер-ключ и его seed
 * как параметры. Это позволяет прогнать бенчмарк несколько раз с разными
 * ключами и сравнить метрики (см. {@link BenchmarkResult#keySeed}).
 * </p>
 *
 * @author FractalCipher Team
 * @see BenchmarkResult
 * @see ImageMetrics
 */
public class BenchmarkRunner {

    /** Утилитарный класс — инстанцирование не нужно. */
    private BenchmarkRunner() {}

    /**
     * СЕРИЯ 1: только перестановка.
     *
     * @param pf            фабрика перестановок
     * @param originalImage исходное изображение
     * @param masterKey     мастер-ключ
     * @param keySeed       seed ключа
     * @return список результатов
     */
    public static List<BenchmarkResult> runPermutationOnly(PermutationSourceFactory pf,
                                                           Image originalImage,
                                                           byte[] masterKey,
                                                           int keySeed) {
        DiffusionSourceFactory nullOnlyFactory = new DiffusionSourceFactory() {{
            clear();
            register(new NullDiffusion());
        }};

        return run(
                pf.availableNames(),
                nullOnlyFactory.availableNames(),
                pf, nullOnlyFactory,
                originalImage,
                EncryptionMode.PERMUTATION_ONLY,
                masterKey, keySeed
        );
    }

    /**
     * СЕРИЯ 2: только диффузия.
     *
     * @param df            фабрика диффузий
     * @param originalImage исходное изображение
     * @param masterKey     мастер-ключ
     * @param keySeed       seed ключа
     * @return список результатов
     */
    public static List<BenchmarkResult> runDiffusionOnly(DiffusionSourceFactory df,
                                                         Image originalImage,
                                                         byte[] masterKey,
                                                         int keySeed) {
        PermutationSourceFactory identityOnlyFactory = new PermutationSourceFactory() {{
            clear();
            register(new IdentityPermutation());
        }};

        return run(
                identityOnlyFactory.availableNames(),
                df.availableNames(),
                identityOnlyFactory, df,
                originalImage,
                EncryptionMode.DIFFUSION_ONLY,
                masterKey, keySeed
        );
    }

    /**
     * СЕРИЯ 3: полная схема.
     *
     * @param pf            фабрика перестановок
     * @param df            фабрика диффузий
     * @param originalImage исходное изображение
     * @param masterKey     мастер-ключ
     * @param keySeed       seed ключа
     * @return список результатов
     */
    public static List<BenchmarkResult> runFull(PermutationSourceFactory pf,
                                                DiffusionSourceFactory df,
                                                Image originalImage,
                                                byte[] masterKey,
                                                int keySeed) {
        return run(
                pf.availableNames(),
                df.availableNames(),
                pf, df,
                originalImage,
                EncryptionMode.FULL,
                masterKey, keySeed
        );
    }

    /**
     * Общий движок всех трёх серий.
     */
    private static List<BenchmarkResult> run(Iterable<String> permutationNames,
                                             Iterable<String> diffusionNames,
                                             PermutationSourceFactory permutationFactory,
                                             DiffusionSourceFactory diffusionFactory,
                                             Image originalImage,
                                             EncryptionMode mode,
                                             byte[] masterKey,
                                             int keySeed) {
        Image modifiedImage = withOnePixelFlipped(originalImage);
        List<String> permutations = new ArrayList<>(permutationFactory.availableNames());
        List<String> diffusions = new ArrayList<>(diffusionFactory.availableNames());

        List<String[]> pairs = new ArrayList<>();
        for (String p : permutations) {
            for (String d : diffusions) {
                pairs.add(new String[]{p, d});
            }
        }

        List<BenchmarkResult> results =
                java.util.Collections.synchronizedList(new ArrayList<>());

        pairs.parallelStream().forEach(pair -> {
            String permutationName = pair[0];
            String diffusionName = pair[1];
            try {
                long permSeed = KeyDerivation.deriveLong(
                        masterKey, "permutation:" + permutationName);
                double x0 = KeyDerivation.deriveDouble(
                        masterKey, "diffusion:" + diffusionName + ":x0");
                long sboxSeed = KeyDerivation.deriveLong(
                        masterKey, "diffusion:" + diffusionName + ":sbox");
                long sboxCbc1 = KeyDerivation.deriveLong(
                        masterKey, "diffusion:" + diffusionName + ":cbc1");
                long sboxCbc2 = KeyDerivation.deriveLong(
                        masterKey, "diffusion:" + diffusionName + ":cbc2");

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

                cipher.setImageInput(encrypted);
                cipher.decrypt();
                Image decrypted = cipher.getImageOutput();

                double mseValue = ImageMetrics.mse(
                        originalImage.getImage(), decrypted.getImage());

                results.add(new BenchmarkResult(
                        originalImage.getName(),
                        originalImage.isBlackAndWhite(),
                        originalImage.getResolution(),
                        keySeed,
                        permutationName, diffusionName, elapsed,
                        ImageMetrics.correlationCoefficient(encrypted.getImage()),
                        ImageMetrics.entropy(encrypted.getImage()),
                        ImageMetrics.npcr(encrypted.getImage(), encryptedModified.getImage()),
                        ImageMetrics.uaci(encrypted.getImage(), encryptedModified.getImage()),
                        mseValue
                ));
            } catch (Exception e) {
                System.out.println("[Пропущено, seed=" + keySeed + "] "
                        + permutationName + " + " + diffusionName + ": " + e.getMessage());
            }
        });
        return results;
    }

    private static Image withOnePixelFlipped(Image image) {
        BufferedImage buffered = image.getImage();
        int width = buffered.getWidth();
        int height = buffered.getHeight();

        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, width, height,
                buffered.getRGB(0, 0, width, height, null, 0, width), 0, width);

        int pixel = copy.getRGB(0, 0);
        copy.setRGB(0, 0, pixel ^ 0x00000001);

        return new Image(image.getId() + "_flip", image.getName(),
                copy, image.getSize(), image.getFileType());
    }
}