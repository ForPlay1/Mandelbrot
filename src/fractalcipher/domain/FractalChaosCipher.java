package fractalcipher.domain;

import fractalcipher.model.diffusion.DiffusionSource;
import fractalcipher.model.permutation.PermutationSource;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import fractalcipher.exception.*;

/**
 * Конкретная реализация {@link Cipher}, объединяющая фрактальную перестановку
 * и хаотическую диффузию.
 * <p>
 * Схема шифрования:
 * <ol>
 *   <li><b>preprocess</b> — чтение пикселей, генерация перестановки
 *       (в режимах {@link EncryptionMode#PERMUTATION_ONLY} и {@link EncryptionMode#FULL})
 *       или identity-перестановки (в {@link EncryptionMode#DIFFUSION_ONLY}).</li>
 *   <li><b>process</b> — перестановка пикселей, затем диффузия:
 *       XOR с потоком (упрощённая схема) либо два прохода CBC с S-боксами
 *       (только {@link EncryptionMode#FULL}).</li>
 *   <li><b>postprocess</b> — сборка {@link Image} из обработанных пикселей.</li>
 * </ol>
 * </p>
 * <p>
 * Расшифровка реализована отдельным методом {@link #decrypt()} и снимает
 * шаги в обратном порядке.
 * </p>
 *
 * @author FractalCipher Team
 * @see Cipher
 * @see PermutationSource
 * @see DiffusionSource
 */
public class FractalChaosCipher extends Cipher {

    /** Источник перестановки пикселей (фрактал). */
    private final PermutationSource permutationSource;

    /** Источник потока байт для диффузии (хаотическая карта). */
    private final DiffusionSource diffusionSource;

    /** Параметры генерации перестановки (seed, границы области и т.п.). */
    private final Map<String, Double> permutationParams;

    /** Параметры генерации потока диффузии (seed, x0, параметры карты). */
    private final Map<String, Double> diffusionParams;

    /** Режим: какие этапы применять. */
    private final EncryptionMode mode;

    /** Ширина обрабатываемого изображения (заполняется в {@link #preprocess()}). */
    private int width;

    /** Высота обрабатываемого изображения (заполняется в {@link #preprocess()}). */
    private int height;

    /** Промежуточный массив пикселей (ARGB int). */
    private int[] pixels;

    /** Сгенерированная перестановка (заполняется в {@link #preprocess()}). */
    private int[] permutation;

    /**
     * Прямой S-бокс для первого прохода CBC.
     * Инициализируется только в режиме {@link EncryptionMode#FULL}, иначе {@code null}.
     */
    private final int[] sbox1;

    /**
     * Прямой S-бокс для второго (обратного) прохода CBC.
     * Инициализируется только в режиме {@link EncryptionMode#FULL}, иначе {@code null}.
     */
    private final int[] sbox2;

    /** Обратный S-бокс для первого прохода (используется при расшифровке). */
    private final int[] invSbox1;

    /** Обратный S-бокс для второго прохода (используется при расшифровке). */
    private final int[] invSbox2;

    /**
     * Конструктор по умолчанию: пустые параметры, режим {@link EncryptionMode#FULL}.
     * <p>Оставлен для обратной совместимости.</p>
     *
     * @param id                идентификатор шифратора
     * @param name              человекочитаемое имя
     * @param permutationSource источник перестановки
     * @param diffusionSource   источник диффузии
     */
    public FractalChaosCipher(String id, String name,
                              PermutationSource permutationSource,
                              DiffusionSource diffusionSource) {
        this(id, name, permutationSource, diffusionSource,
                new HashMap<>(), new HashMap<>(), EncryptionMode.FULL);
    }

    /**
     * Конструктор с параметрами, режим {@link EncryptionMode#FULL}.
     *
     * @param id                 идентификатор шифратора
     * @param name               человекочитаемое имя
     * @param permutationSource  источник перестановки
     * @param diffusionSource    источник диффузии
     * @param permutationParams  параметры перестановки
     * @param diffusionParams    параметры диффузии
     */
    public FractalChaosCipher(String id, String name,
                              PermutationSource permutationSource,
                              DiffusionSource diffusionSource,
                              Map<String, Double> permutationParams,
                              Map<String, Double> diffusionParams) {
        this(id, name, permutationSource, diffusionSource,
                permutationParams, diffusionParams, EncryptionMode.FULL);

    }

    /**
     * Полный конструктор с явным указанием режима.
     * <p>
     * В режиме {@link EncryptionMode#FULL} из параметров диффузии извлекаются
     * seed-ы S-боксов ({@code "sboxCbc1"}, {@code "sboxCbc2"}) и один раз
     * строятся прямые и обратные таблицы замен.
     * </p>
     *
     * @param id                 идентификатор шифратора
     * @param name               человекочитаемое имя
     * @param permutationSource  источник перестановки
     * @param diffusionSource    источник диффузии
     * @param permutationParams  параметры перестановки
     * @param diffusionParams    параметры диффузии (могут содержать seed-ы S-боксов)
     * @param mode               режим шифрования
     */
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
        // === ИНИЦИАЛИЗАЦИЯ S-БОКСОВ (ОДИН РАЗ) ===
        if (mode == EncryptionMode.FULL) {
            long sboxSeed1 = diffusionParams.getOrDefault("sboxCbc1", 111.0).longValue();
            long sboxSeed2 = diffusionParams.getOrDefault("sboxCbc2", 222.0).longValue();

            this.sbox1 = buildSBox(sboxSeed1);
            this.sbox2 = buildSBox(sboxSeed2);

            this.invSbox1 = buildInverseSBox(this.sbox1);
            this.invSbox2 = buildInverseSBox(this.sbox2);
        } else {
            this.sbox1 = null;
            this.sbox2 = null;
            this.invSbox1 = null;
            this.invSbox2 = null;
        }
    }

    /**
     * Читает пиксели из {@link #imageInput} и генерирует перестановку.
     * <p>
     * В режимах {@link EncryptionMode#PERMUTATION_ONLY} и {@link EncryptionMode#FULL}
     * перестановка берётся у {@link #permutationSource}; в {@link EncryptionMode#DIFFUSION_ONLY}
     * используется identity-перестановка.
     * </p>
     *
     * @throws EncryptionFailedException если источник перестановки не смог
     *         сгенерировать корректный массив индексов
     */
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
            try {
                permutation = permutationSource.generatePermutation(pixels.length, params);
            } catch (EncryptionFailedException e) {
                // Пробрасываем как есть — категория уже ENCRYPTION
                throw e;
            } catch (RuntimeException e) {
                // БЫЛО: исключение пробрасывалось как есть.
                // СТАЛО: оборачиваем в EncryptionFailedException для
                // единообразной классификации.
                throw new EncryptionFailedException(
                        "Ошибка генерации перестановки: " + e.getMessage(), e);
            }
        } else {
            // DIFFUSION_ONLY: перестановка = identity (ничего не меняем)
            permutation = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) permutation[i] = i;
        }
    }

    /**
     * Применяет перестановку и диффузию к пикселям.
     * <p>
     * В {@link EncryptionMode#FULL} используется двухпроходная схема CBC с S-боксами;
     * в остальных режимах — простой XOR с потоком (в {@link EncryptionMode#PERMUTATION_ONLY}
     * поток нулевой, поэтому данные не меняются).
     * </p>
     *
     * @throws EncryptionFailedException если источник диффузии не смог
     *         сгенерировать поток байт
     */
    @Override
    protected void process() {
        int[] scrambled = applyPermutation(pixels, permutation);

        // === ДИФФУЗИЯ: применяется только в DIFFUSION_ONLY и FULL ===
        byte[] stream;
        if (mode == EncryptionMode.DIFFUSION_ONLY || mode == EncryptionMode.FULL) {
            try {
                stream = diffusionSource.generateStream(pixels.length * 8, diffusionParams);
            } catch (EncryptionFailedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EncryptionFailedException(
                        "Ошибка генерации потока диффузии: " + e.getMessage(), e);
            }
        } else {
            // PERMUTATION_ONLY: поток нулевой, но XOR всё равно применяется
            // (это не меняет данные: x ^ 0 = x)
            stream = new byte[pixels.length * 8];
        }

        pixels = applyDiffusionEncrypt(scrambled, stream);
    }

    /**
     * Собирает {@link Image} из обработанных пикселей и сохраняет в {@link #imageOutput}.
     */
    @Override
    protected void postprocess() {
        BufferedImage result = toImage(pixels, width, height);
        imageOutput = new Image(
                imageInput.getId() + "_enc",
                imageInput.getName() + " (encrypted via " + getName() + " [" + mode + "])",
                result, estimateSize(result), imageInput.getFileType()
        );
    }

    /**
     * Расшифровывает изображение, снимая шаги шифрования в обратном порядке.
     * <p>
     * Сначала применяется обратная диффузия (с использованием {@code invSbox*}
     * в режиме {@link EncryptionMode#FULL}), затем — обратная перестановка.
     * </p>
     * <p>
     * Результат сохраняется в {@link #imageOutput}.
     * </p>
     *
     * @throws EncryptionFailedException если не удалось сгенерировать поток
     *         диффузии или перестановку
     */
    @Override
    public void decrypt() {
        BufferedImage buffered = imageInput.getImage();
        int w = buffered.getWidth();
        int h = buffered.getHeight();
        int[] cipherPixels = buffered.getRGB(0, 0, w, h, null, 0, w);

        byte[] stream;
        if (mode == EncryptionMode.DIFFUSION_ONLY || mode == EncryptionMode.FULL) {
            try {
                stream = diffusionSource.generateStream(cipherPixels.length * 8, diffusionParams);
            } catch (EncryptionFailedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EncryptionFailedException(
                        "Ошибка генерации потока диффузии: " + e.getMessage(), e);
            }
        } else {
            stream = new byte[cipherPixels.length * 8];
        }
        int[] unDiffused = applyDiffusionDecrypt(cipherPixels, stream);

        int[] perm;
        if (mode == EncryptionMode.PERMUTATION_ONLY || mode == EncryptionMode.FULL) {
            Map<String, Double> params = new HashMap<>(permutationParams);
            params.put("width", (double) w);
            params.put("height", (double) h);
            try {
                perm = permutationSource.generatePermutation(cipherPixels.length, params);
            } catch (EncryptionFailedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EncryptionFailedException(
                        "Ошибка генерации перестановки: " + e.getMessage(), e);
            }
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

    /**
     * Применяет перестановку: {@code result[i] = pixels[permutation[i]]}.
     *
     * @param pixels      исходные пиксели
     * @param permutation массив индексов
     * @return новый массив с переставленными пикселями
     */
    private int[] applyPermutation(int[] pixels, int[] permutation) {
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            result[i] = pixels[permutation[i]];
        }
        return result;
    }

    /**
     * Обращает перестановку: {@code inverse[permutation[i]] = i}.
     *
     * @param permutation прямая перестановка
     * @return обратная перестановка
     */
    private int[] invertPermutation(int[] permutation) {
        int[] inverse = new int[permutation.length];
        for (int i = 0; i < permutation.length; i++) {
            inverse[permutation[i]] = i;
        }
        return inverse;
    }

    /**
     * Строит S-бокс (перестановку 0..255) на основе seed через
     * перемешивание Фишера–Йетса.
     *
     * @param seed seed генератора {@link java.util.Random}
     * @return массив из 256 уникальных значений
     */
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

    /**
     * Строит обратный S-бокс: {@code inv[sbox[i]] = i}.
     *
     * @param sbox прямой S-бокс
     * @return обратный S-бокс
     */
    private int[] buildInverseSBox(int[] sbox) {
        int[] inv = new int[256];
        for (int i = 0; i < 256; i++) inv[sbox[i]] = i;
        return inv;
    }

    /**
     * Применяет диффузию к пикселям.
     * <p>
     * В {@link EncryptionMode#FULL} — два прохода CBC с S-боксами
     * (вперёд, затем назад). В остальных режимах — простой XOR с потоком.
     * </p>
     *
     * @param pixels пиксели после перестановки
     * @param stream поток байт (длиной {@code 2 * 4 * pixels.length} для FULL)
     * @return новый массив пикселей после диффузии
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

    /**
     * Снимает диффузию (обратная операция к {@link #applyDiffusionEncrypt}).
     * <p>
     * В {@link EncryptionMode#FULL} использует {@code invSbox1}, {@code invSbox2}
     * и знание предыдущего зашифрованного байта для восстановления открытого
     * текста. В остальных режимах — XOR (обратим сам себе).
     * </p>
     *
     * @param pixels зашифрованные пиксели
     * @param stream поток байт (тот же, что при шифровании)
     * @return массив пикселей до диффузии
     */
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

    /**
     * Создаёт {@link BufferedImage} из массива ARGB-пикселей.
     *
     * @param pixels массив ARGB
     * @param width  ширина
     * @param height высота
     * @return новое изображение
     */
    private BufferedImage toImage(int[] pixels, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    /**
     * Оценивает размер изображения в байтах (4 байта на пиксель, RGBA).
     *
     * @param image изображение
     * @return оценочный размер в байтах
     */
    private long estimateSize(BufferedImage image) {
        return (long) image.getWidth() * image.getHeight() * 4;
    }
}