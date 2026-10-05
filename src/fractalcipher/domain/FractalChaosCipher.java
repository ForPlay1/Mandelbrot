package fractalcipher.domain;

import fractalcipher.model.diffusion.DiffusionSource;
import fractalcipher.model.permutation.PermutationSource;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * КОНКРЕТНЫЙ (не абстрактный) класс — единственная реализация схемы
 * "перестановка + диффузия". Вместо подкласса на каждую комбинацию
 * фрактал+карта (это дало бы 10-15 x 5+ = 50+ классов-пустышек),
 * конкретные алгоритмы передаются через конструктор — композиция
 * вместо наследования. "Разные шифраторы" с точки зрения
 * преподавателя — это разные ЭКЗЕМПЛЯРЫ этого класса с разными
 * PermutationSource/DiffusionSource, а не разные классы в коде.
 *
 * Подкласс от Cipher есть смысл писать только тогда, когда у
 * шифратора ДЕЙСТВИТЕЛЬНО другая структура шагов (например,
 * классический AES как baseline для сравнения — там preprocess/
 * process/postprocess будут устроены принципиально иначе).
 */
public class FractalChaosCipher extends Cipher {

    private final PermutationSource permutationSource;
    private final DiffusionSource diffusionSource;
    private final Map<String, Double> permutationParams;
    private final Map<String, Double> diffusionParams;

    // поля, которые "передаются" между шагами preprocess -> process -> postprocess
    private int width;
    private int height;
    private int[] pixels;
    private int[] permutation;

    public FractalChaosCipher(String id, String name,
                              PermutationSource permutationSource,
                              DiffusionSource diffusionSource) {
        this(id, name, permutationSource, diffusionSource, new HashMap<>(), new HashMap<>());
    }

    public FractalChaosCipher(String id, String name,
                              PermutationSource permutationSource,
                              DiffusionSource diffusionSource,
                              Map<String, Double> permutationParams,
                              Map<String, Double> diffusionParams) {
        super(id, name);
        this.permutationSource = permutationSource;
        this.diffusionSource = diffusionSource;
        this.permutationParams = permutationParams;
        this.diffusionParams = diffusionParams;
    }

    // --- Шаг 1 из 3: подготовка ---
    @Override
    protected void preprocess() {
        BufferedImage buffered = imageInput.getImage();
        width = buffered.getWidth();
        height = buffered.getHeight();
        pixels = buffered.getRGB(0, 0, width, height, null, 0, width);

        Map<String, Double> params = new HashMap<>(permutationParams);
        params.put("width", (double) width);
        params.put("height", (double) height);
        permutation = permutationSource.generatePermutation(pixels.length, params);
    }

    // --- Шаг 2 из 3: собственно шифрование ---
    @Override
    protected void process() {
        int[] scrambled = applyPermutation(pixels, permutation);
        byte[] stream = diffusionSource.generateStream(pixels.length * 3, diffusionParams);
        pixels = applyDiffusion(scrambled, stream);
    }

    // --- Шаг 3 из 3: упаковка результата ---
    @Override
    protected void postprocess() {
        BufferedImage result = toImage(pixels, width, height);
        imageOutput = new Image(
                imageInput.getId() + "_enc",
                imageInput.getName() + " (encrypted via " + getName() + ")",
                result, estimateSize(result), imageInput.getFileType()
        );
    }

    @Override
    public void decrypt() {
        BufferedImage buffered = imageInput.getImage();
        int w = buffered.getWidth();
        int h = buffered.getHeight();
        int[] cipherPixels = buffered.getRGB(0, 0, w, h, null, 0, w);

        byte[] stream = diffusionSource.generateStream(cipherPixels.length * 3, diffusionParams);
        int[] unDiffused = applyDiffusion(cipherPixels, stream);

        Map<String, Double> params = new HashMap<>(permutationParams);
        params.put("width", (double) w);
        params.put("height", (double) h);
        int[] perm = permutationSource.generatePermutation(cipherPixels.length, params);
        int[] inverse = invertPermutation(perm);
        int[] original = applyPermutation(unDiffused, inverse);

        BufferedImage result = toImage(original, w, h);
        imageOutput = new Image(
                imageInput.getId() + "_dec",
                imageInput.getName() + " (decrypted via " + getName() + ")",
                result, estimateSize(result), imageInput.getFileType()
        );
    }

    private int[] applyPermutation(int[] pixels, int[] permutation) {
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            result[i] = pixels[permutation[i]];
        }
        return result;
    }

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

    private long estimateSize(BufferedImage image) {
        return (long) image.getWidth() * image.getHeight() * 4;
    }
}