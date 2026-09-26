package fractalcipher.model;

import fractalcipher.model.diffusion.DiffusionSource;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSource;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Реализована по-настоящему: перестановка (confusion) через
 * PermutationSource, затем XOR-диффузия через DiffusionSource —
 * ровно та схема, которую вы обсуждали с DeepSeek. Конкретные
 * формулы фракталов/карт — в их собственных классах; этот класс
 * их не знает, только вызывает через интерфейсы.
 */
public class ImageCipherImpl implements ImageCipher {

    private final PermutationSourceFactory permutationFactory;
    private final DiffusionSourceFactory diffusionFactory;

    public ImageCipherImpl(PermutationSourceFactory permutationFactory, DiffusionSourceFactory diffusionFactory) {
        this.permutationFactory = permutationFactory;
        this.diffusionFactory = diffusionFactory;
    }

    @Override
    public BufferedImage encrypt(BufferedImage image, EncryptionKey key) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        int[] permutation = getPermutation(width, height, key);
        int[] scrambled = applyPermutation(pixels, permutation);

        byte[] stream = getDiffusionStream(pixels.length * 3, key);
        int[] diffused = applyDiffusion(scrambled, stream);

        return toImage(diffused, width, height);
    }

    @Override
    public BufferedImage decrypt(BufferedImage image, EncryptionKey key) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        byte[] stream = getDiffusionStream(pixels.length * 3, key);
        int[] unDiffused = applyDiffusion(pixels, stream); // XOR обратим сам себе

        int[] permutation = getPermutation(width, height, key);
        int[] inverse = invertPermutation(permutation);
        int[] original = applyPermutation(unDiffused, inverse);

        return toImage(original, width, height);
    }

    private int[] getPermutation(int width, int height, EncryptionKey key) {
        PermutationSource source = permutationFactory.get(key.permutationSourceName);
        Map<String, Double> params = new HashMap<>(key.permutationParams);
        params.put("width", (double) width);
        params.put("height", (double) height);
        return source.generatePermutation(width * height, params);
    }

    private byte[] getDiffusionStream(int length, EncryptionKey key) {
        DiffusionSource source = diffusionFactory.get(key.diffusionSourceName);
        return source.generateStream(length, key.diffusionParams);
    }

    private int[] applyPermutation(int[] pixels, int[] permutation) {
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            result[i] = pixels[permutation[i]];
        }
        return result;
    }

    /** inverse[permutation[i]] = i — стандартная инверсия для отката перестановки. */
    private int[] invertPermutation(int[] permutation) {
        int[] inverse = new int[permutation.length];
        for (int i = 0; i < permutation.length; i++) {
            inverse[permutation[i]] = i;
        }
        return inverse;
    }

    private int[] applyDiffusion(int[] pixels, byte[] stream) {
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            r ^= stream[i * 3] & 0xFF;
            g ^= stream[i * 3 + 1] & 0xFF;
            b ^= stream[i * 3 + 2] & 0xFF;

            result[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return result;
    }

    private BufferedImage toImage(int[] pixels, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
}