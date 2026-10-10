package fractalcipher.domain;

import fractalcipher.model.diffusion.DiffusionSource;
import fractalcipher.model.permutation.PermutationSource;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class FractalChaosCipher extends Cipher {

    private final PermutationSource permutationSource;
    private final DiffusionSource diffusionSource;
    private final Map<String, Double> permutationParams;
    private final Map<String, Double> diffusionParams;
    private final EncryptionMode mode;  // <-- НОВОЕ

    private int width;
    private int height;
    private int[] pixels;
    private int[] permutation;

    // Старый конструктор — по умолчанию FULL (для обратной совместимости)
    public FractalChaosCipher(String id, String name,
                              PermutationSource permutationSource,
                              DiffusionSource diffusionSource) {
        this(id, name, permutationSource, diffusionSource,
                new HashMap<>(), new HashMap<>(), EncryptionMode.FULL);
    }

    public FractalChaosCipher(String id, String name,
                              PermutationSource permutationSource,
                              DiffusionSource diffusionSource,
                              Map<String, Double> permutationParams,
                              Map<String, Double> diffusionParams) {
        this(id, name, permutationSource, diffusionSource,
                permutationParams, diffusionParams, EncryptionMode.FULL);
    }

    // Новый конструктор с режимом
    public FractalChaosCipher(String id, String name,
                              PermutationSource permutationSource,
                              DiffusionSource diffusionSource,
                              Map<String, Double> permutationParams,
                              Map<String, Double> diffusionParams,
                              EncryptionMode mode) {
        super(id, name);
        this.permutationSource = permutationSource;
        this.diffusionSource = diffusionSource;
        this.permutationParams = permutationParams;
        this.diffusionParams = diffusionParams;
        this.mode = mode;
    }

    @Override
    protected void preprocess() {
        BufferedImage buffered = imageInput.getImage();
        width = buffered.getWidth();
        height = buffered.getHeight();
        pixels = buffered.getRGB(0, 0, width, height, null, 0, width);

        // === ПЕРЕСТАНОВКА: применяется только в PERMUTATION_ONLY и FULL ===
        if (mode == EncryptionMode.PERMUTATION_ONLY || mode == EncryptionMode.FULL) {
            Map<String, Double> params = new HashMap<>(permutationParams);
            params.put("width", (double) width);
            params.put("height", (double) height);
            permutation = permutationSource.generatePermutation(pixels.length, params);
        } else {
            // DIFFUSION_ONLY: перестановка = identity (ничего не меняем)
            permutation = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) permutation[i] = i;
        }
    }

    @Override
    protected void process() {
        int[] scrambled = applyPermutation(pixels, permutation);

        // === ДИФФУЗИЯ: применяется только в DIFFUSION_ONLY и FULL ===
        byte[] stream;
        if (mode == EncryptionMode.DIFFUSION_ONLY || mode == EncryptionMode.FULL) {
            stream = diffusionSource.generateStream(pixels.length * 8, diffusionParams);
        } else {
            // PERMUTATION_ONLY: поток нулевой, но XOR всё равно применяется
            // (это не меняет данные: x ^ 0 = x)
            stream = new byte[pixels.length * 8];
        }

        pixels = applyDiffusionEncrypt(scrambled, stream);
    }

    @Override
    protected void postprocess() {
        BufferedImage result = toImage(pixels, width, height);
        imageOutput = new Image(
                imageInput.getId() + "_enc",
                imageInput.getName() + " (encrypted via " + getName() + " [" + mode + "])",
                result, estimateSize(result), imageInput.getFileType()
        );
    }

    @Override
    public void decrypt() {
        BufferedImage buffered = imageInput.getImage();
        int w = buffered.getWidth();
        int h = buffered.getHeight();
        int[] cipherPixels = buffered.getRGB(0, 0, w, h, null, 0, w);

        byte[] stream;
        if (mode == EncryptionMode.DIFFUSION_ONLY || mode == EncryptionMode.FULL) {
            stream = diffusionSource.generateStream(cipherPixels.length * 8, diffusionParams);
        } else {
            stream = new byte[cipherPixels.length * 8];
        }
        int[] unDiffused = applyDiffusionDecrypt(cipherPixels, stream);

        int[] perm;
        if (mode == EncryptionMode.PERMUTATION_ONLY || mode == EncryptionMode.FULL) {
            Map<String, Double> params = new HashMap<>(permutationParams);
            params.put("width", (double) w);
            params.put("height", (double) h);
            perm = permutationSource.generatePermutation(cipherPixels.length, params);
        } else {
            perm = new int[cipherPixels.length];
            for (int i = 0; i < cipherPixels.length; i++) perm[i] = i;
        }
        int[] inverse = invertPermutation(perm);
        int[] original = applyPermutation(unDiffused, inverse);

        BufferedImage result = toImage(original, w, h);
        imageOutput = new Image(
                imageInput.getId() + "_dec",
                imageInput.getName() + " (decrypted via " + getName() + " [" + mode + "])",
                result, estimateSize(result), imageInput.getFileType()
        );
    }

    // --- Вспомогательные методы (без изменений) ---

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

    private int[] buildSBox(long seed) {
        int[] sbox = new int[256];
        for (int i = 0; i < 256; i++) sbox[i] = i;
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = sbox[i]; sbox[i] = sbox[j]; sbox[j] = tmp;
        }
        return sbox;
    }

    private int[] buildInverseSBox(int[] sbox) {
        int[] inv = new int[256];
        for (int i = 0; i < 256; i++) inv[sbox[i]] = i;
        return inv;
    }

    /**
     * CBC с S-боксами. Применяется ТОЛЬКО в режиме FULL.
     * В PERMUTATION_ONLY и DIFFUSION_ONLY — просто XOR со stream.
     */
    private int[] applyDiffusionEncrypt(int[] pixels, byte[] stream) {
        int n = pixels.length;

        // Разворачиваем ARGB в плоский массив каналов
        byte[] ch = new byte[4 * n];
        for (int i = 0; i < n; i++) {
            int argb = pixels[i];
            ch[i*4]     = (byte) ((argb >> 24) & 0xFF);
            ch[i*4 + 1] = (byte) ((argb >> 16) & 0xFF);
            ch[i*4 + 2] = (byte) ((argb >> 8)  & 0xFF);
            ch[i*4 + 3] = (byte) ( argb        & 0xFF);
        }

        // === ВЫБОР РЕЖИМА ===
        if (mode == EncryptionMode.FULL) {
            // Полная схема: CBC + S-боксы
            long sboxSeed1 = diffusionParams.getOrDefault("sboxCbc1", 111.0).longValue();
            long sboxSeed2 = diffusionParams.getOrDefault("sboxCbc2", 222.0).longValue();
            int[] sbox1 = buildSBox(sboxSeed1);
            int[] sbox2 = buildSBox(sboxSeed2);

            // ПРОХОД 1: вперёд
            int prev = 0;
            for (int i = 0; i < 4 * n; i++) {
                int s = stream[i] & 0xFF;
                int c = ((ch[i] & 0xFF) + s + prev) & 0xFF;
                c = sbox1[c];
                ch[i] = (byte) c;
                prev = c;
            }

            // ПРОХОД 2: назад
            prev = 0;
            for (int i = 4 * n - 1; i >= 0; i--) {
                int s = stream[4 * n + i] & 0xFF;
                int c = ((ch[i] & 0xFF) + s + prev) & 0xFF;
                c = sbox2[c];
                ch[i] = (byte) c;
                prev = c;
            }
        } else {
            // Упрощённая схема: только XOR, без CBC и S-боксов
            for (int i = 0; i < 4 * n; i++) {
                ch[i] = (byte) ((ch[i] & 0xFF) ^ (stream[i] & 0xFF));
            }
        }

        // Собираем пиксели обратно
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = ((ch[i*4]     & 0xFF) << 24)
                    | ((ch[i*4 + 1] & 0xFF) << 16)
                    | ((ch[i*4 + 2] & 0xFF) << 8)
                    |  (ch[i*4 + 3] & 0xFF);
        }
        return result;
    }

    private int[] applyDiffusionDecrypt(int[] pixels, byte[] stream) {
        int n = pixels.length;

        byte[] ch = new byte[4 * n];
        for (int i = 0; i < n; i++) {
            int argb = pixels[i];
            ch[i*4]     = (byte) ((argb >> 24) & 0xFF);
            ch[i*4 + 1] = (byte) ((argb >> 16) & 0xFF);
            ch[i*4 + 2] = (byte) ((argb >> 8)  & 0xFF);
            ch[i*4 + 3] = (byte) ( argb        & 0xFF);
        }

        if (mode == EncryptionMode.FULL) {
            // Обратный порядок: сначала снимаем проход 2, потом проход 1
            long sboxSeed1 = diffusionParams.getOrDefault("sboxCbc1", 111.0).longValue();
            long sboxSeed2 = diffusionParams.getOrDefault("sboxCbc2", 222.0).longValue();
            int[] invSbox1 = buildInverseSBox(buildSBox(sboxSeed1));
            int[] invSbox2 = buildInverseSBox(buildSBox(sboxSeed2));

            // СНИМАЕМ ПРОХОД 2
            int prev = 0;
            for (int i = 4 * n - 1; i >= 0; i--) {
                int s = stream[4 * n + i] & 0xFF;
                int c_enc = ch[i] & 0xFF;
                int c_pre = invSbox2[c_enc];
                int plain = (c_pre - s - prev) & 0xFF;
                ch[i] = (byte) plain;
                prev = c_enc;
            }

            // СНИМАЕМ ПРОХОД 1
            prev = 0;
            for (int i = 0; i < 4 * n; i++) {
                int s = stream[i] & 0xFF;
                int c_enc = ch[i] & 0xFF;
                int c_pre = invSbox1[c_enc];
                int plain = (c_pre - s - prev) & 0xFF;
                ch[i] = (byte) plain;
                prev = c_enc;
            }
        } else {
            // Упрощённая схема: XOR обратим сам себе
            for (int i = 0; i < 4 * n; i++) {
                ch[i] = (byte) ((ch[i] & 0xFF) ^ (stream[i] & 0xFF));
            }
        }

        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = ((ch[i*4]     & 0xFF) << 24)
                    | ((ch[i*4 + 1] & 0xFF) << 16)
                    | ((ch[i*4 + 2] & 0xFF) << 8)
                    |  (ch[i*4 + 3] & 0xFF);
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