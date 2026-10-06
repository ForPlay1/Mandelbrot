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

    @Override
    protected void process() {
        int[] scrambled = applyPermutation(pixels, permutation);

        // 2 прохода × 4 канала (ARGB) = 8 байт на пиксель
        byte[] stream = diffusionSource.generateStream(pixels.length * 8, diffusionParams);

        pixels = applyDiffusionEncrypt(scrambled, stream);
    }

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

        byte[] stream = diffusionSource.generateStream(cipherPixels.length * 8, diffusionParams);
        int[] unDiffused = applyDiffusionDecrypt(cipherPixels, stream);

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
    // Два S-бокса: один для прямого прохода, другой для обратного.
    // Генерируются детерминированно из ключа (передаются в diffusionParams
    // или деривируются отдельно).
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
     * Шифрование диффузией с CBC-сцеплением и двумя проходами.
     *
     * Проход 1 (вперёд):  c_i = p_i ^ s_i ^ c_{i-1}
     * Проход 2 (назад):   d_i = c_i ^ t_i ^ d_{i+1}
     *
     * Здесь s_i и t_i — разные половины хаотического потока,
     * чтобы сцепления не «сокращались» в формуле.
     */
    private int[] applyDiffusionEncrypt(int[] pixels, byte[] stream) {
        int n = pixels.length;

        long sboxSeed1 = diffusionParams.getOrDefault("sboxCbc1", 111.0).longValue();
        long sboxSeed2 = diffusionParams.getOrDefault("sboxCbc2", 222.0).longValue();
        int[] sbox1 = buildSBox(sboxSeed1);
        int[] sbox2 = buildSBox(sboxSeed2);

        // Разворачиваем ARGB в плоский массив каналов
        byte[] ch = new byte[4 * n];
        for (int i = 0; i < n; i++) {
            int argb = pixels[i];
            ch[i*4]     = (byte) ((argb >> 24) & 0xFF);
            ch[i*4 + 1] = (byte) ((argb >> 16) & 0xFF);
            ch[i*4 + 2] = (byte) ((argb >> 8)  & 0xFF);
            ch[i*4 + 3] = (byte) ( argb        & 0xFF);
        }

        // === ПРОХОД 1: вперёд с S-боксом на состоянии ===
        int prev = 0;
        for (int i = 0; i < 4 * n; i++) {
            int s = stream[i] & 0xFF;
            int c = ((ch[i] & 0xFF) + s + prev) & 0xFF;
            c = sbox1[c];  // <<< НЕЛИНЕЙНОСТЬ
            ch[i] = (byte) c;
            prev = c;
        }

        // === ПРОХОД 2: назад с S-боксом на состоянии ===
        prev = 0;
        for (int i = 4 * n - 1; i >= 0; i--) {
            int s = stream[4 * n + i] & 0xFF;
            int c = ((ch[i] & 0xFF) + s + prev) & 0xFF;
            c = sbox2[c];  // <<< НЕЛИНЕЙНОСТЬ
            ch[i] = (byte) c;
            prev = c;
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

    /**
     * Расшифровка — обратные операции в обратном порядке:
     * сначала снимаем проход 2 (назад), затем проход 1 (вперёд).
     */
    private int[] applyDiffusionDecrypt(int[] pixels, byte[] stream) {
        int n = pixels.length;

        long sboxSeed1 = diffusionParams.getOrDefault("sboxCbc1", 111.0).longValue();
        long sboxSeed2 = diffusionParams.getOrDefault("sboxCbc2", 222.0).longValue();
        int[] invSbox1 = buildInverseSBox(buildSBox(sboxSeed1));
        int[] invSbox2 = buildInverseSBox(buildSBox(sboxSeed2));

        byte[] ch = new byte[4 * n];
        for (int i = 0; i < n; i++) {
            int argb = pixels[i];
            ch[i*4]     = (byte) ((argb >> 24) & 0xFF);
            ch[i*4 + 1] = (byte) ((argb >> 16) & 0xFF);
            ch[i*4 + 2] = (byte) ((argb >> 8)  & 0xFF);
            ch[i*4 + 3] = (byte) ( argb        & 0xFF);
        }

        // === СНИМАЕМ ПРОХОД 2 (назад) ===
        int prev = 0;
        for (int i = 4 * n - 1; i >= 0; i--) {
            int s = stream[4 * n + i] & 0xFF;
            int c_enc = ch[i] & 0xFF;                  // зашифрованное значение
            int c_pre = invSbox2[c_enc];               // undo S-box
            int plain = (c_pre - s - prev) & 0xFF;     // undo сложение
            ch[i] = (byte) plain;
            prev = c_enc;                              // важно: prev = ЗАШИФРОВАННОЕ
        }

        // === СНИМАЕМ ПРОХОД 1 (вперёд) ===
        prev = 0;
        for (int i = 0; i < 4 * n; i++) {
            int s = stream[i] & 0xFF;
            int c_enc = ch[i] & 0xFF;
            int c_pre = invSbox1[c_enc];
            int plain = (c_pre - s - prev) & 0xFF;
            ch[i] = (byte) plain;
            prev = c_enc;
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

    // Упаковка 4 байт потока в один int (ARGB)
    private int packStream(byte[] stream, int offset) {
        return ((stream[offset]     & 0xFF) << 24)
                | ((stream[offset + 1] & 0xFF) << 16)
                | ((stream[offset + 2] & 0xFF) << 8)
                |  (stream[offset + 3] & 0xFF);
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