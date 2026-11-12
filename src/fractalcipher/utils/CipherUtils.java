package fractalcipher.utils;

import fractalcipher.domain.Image;
import fractalcipher.model.KeyDerivation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Утилиты, общие для контроллера и View.
 * <p>
 * Содержит:
 * </p>
 * <ul>
 *   <li>деривацию параметров шифра из мастер-ключа;</li>
 *   <li>правила именования файлов (суффиксы, безопасные имена);</li>
 *   <li>проверку, является ли файл изображением для шифрования.</li>
 * </ul>
 * <p>
 * Методы именования ({@link #safeStem}, {@link #baseName},
 * {@link #ENCRYPTED_SUFFIX}) используются <b>и</b> в
 * {@code CipherController} (там файлы пишутся), <b>и</b> в
 * {@code MainView} (там проверяется, не перезапишется ли что-то).
 * Если поменять правило в одном месте — сломается проверка в другом.
 * Поэтому все правила именования вынесены сюда.
 * </p>
 *
 * @author FractalCipher Team
 * @see KeyDerivation
 */
public class CipherUtils {

    /**
     * Суффикс зашифрованных файлов. Пример: {@code photo_png_encrypted.png}.
     * <p>
     * Важно: суффикс <b>не содержит</b> расширения — оно добавляется
     * отдельно (всегда {@code ".png"}, чтобы не терять качество при
     * повторном сжатии).
     * </p>
     */
    public static final String ENCRYPTED_SUFFIX = "_encrypted";

    /** Список поддерживаемых расширений изображений (без точки, в нижнем регистре). */
    public static final List<String> IMAGE_EXTENSIONS = Image.getImageExtensions();

    /** Утилитарный класс — инстанцирование не нужно. */
    private CipherUtils() {}

    /**
     * Генерирует параметры для источника перестановки на основе мастер-сида.
     * <p>
     * Домен {@code "perm:" + permName} обеспечивает доменную сепарацию:
     * два разных фрактала получат разные seed-ы из одного и того же
     * мастер-ключа.
     * </p>
     *
     * @param masterKey мастер-ключ (32 байта)
     * @param permName  строковое имя источника (например, {@code "mandelbrot"})
     * @return карта с ключом {@code "seed"} для передачи в
     *         {@link fractalcipher.model.permutation.PermutationSource#generatePermutation}
     */
    public static Map<String, Double> buildPermParams(byte[] masterKey, String permName) {
        Map<String, Double> map = new HashMap<>();
        map.put("seed", (double) KeyDerivation.deriveLong(masterKey, "perm:" + permName));
        return map;
    }

    /**
     * Генерирует параметры для источника диффузии (включая S-боксы).
     * <p>
     * Помимо seed-а потока, из мастер-ключа выводятся:
     * </p>
     * <ul>
     *   <li>{@code x0} — начальное значение хаотической карты;</li>
     *   <li>{@code sboxSeed} — seed для S-бокса внутри потока;</li>
     *   <li>{@code sboxCbc1}, {@code sboxCbc2} — seed-ы S-боксов для
     *       двух проходов CBC (используются только в
     *       {@link fractalcipher.domain.EncryptionMode#FULL}).</li>
     * </ul>
     *
     * @param masterKey мастер-ключ (32 байта)
     * @param diffName  строковое имя источника (например, {@code "logistic"})
     * @return карта параметров для
     *         {@link fractalcipher.model.diffusion.DiffusionSource#generateStream}
     */
    public static Map<String, Double> buildDiffParams(byte[] masterKey, String diffName) {
        Map<String, Double> map = new HashMap<>();
        map.put("seed", (double) KeyDerivation.deriveLong(masterKey, "diff:" + diffName));
        map.put("x0", KeyDerivation.deriveDouble(masterKey, "x0:" + diffName));
        map.put("sboxSeed", (double) KeyDerivation.deriveLong(masterKey, "sbox:" + diffName));
        map.put("sboxCbc1", (double) KeyDerivation.deriveLong(masterKey, "cbc1:" + diffName));
        map.put("sboxCbc2", (double) KeyDerivation.deriveLong(masterKey, "cbc2:" + diffName));
        return map;
    }

    /**
     * Проверяет, является ли файл картинкой, которую нужно зашифровать.
     * <p>
     * Возвращает {@code false}, если:
     * </p>
     * <ul>
     *   <li>у файла нет расширения;</li>
     *   <li>расширение не входит в {@link #IMAGE_EXTENSIONS};</li>
     *   <li>имя уже оканчивается на {@link #ENCRYPTED_SUFFIX} —
     *       чтобы пакетное шифрование не трогало уже зашифрованные файлы.</li>
     * </ul>
     *
     * @param fileName имя файла (не полный путь)
     * @return {@code true}, если файл подходит для шифрования
     */
    public static boolean isImageToEncrypt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext) && !baseName(fileName).endsWith(ENCRYPTED_SUFFIX);
    }

    /**
     * Возвращает имя файла без расширения.
     * <p>
     * Пример: {@code "photo.png"} → {@code "photo"}. Если расширения нет —
     * возвращает имя целиком.
     * </p>
     *
     * @param fileName имя файла
     * @return имя без последней точки и всего, что после неё
     */
    public static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    /**
     * Безопасное «ядро» имени: точки заменяются на подчёркивания.
     * <p>
     * Решает проблему коллизий: {@code photo.png} и {@code photo.jpg} —
     * разные файлы, но {@link #baseName} от обоих даёт {@code "photo"}.
     * После {@code safeStem} получаются разные имена
     * ({@code "photo_png"} и {@code "photo_jpg"}), поэтому зашифрованные
     * файлы не затирают друг друга.
     * </p>
     * <p>
     * Пример: {@code "photo.png"} → {@code "photo_png"},
     * {@code "archive.tar.gz"} → {@code "archive_tar_gz"}.
     * </p>
     *
     * @param fileName имя файла с расширением
     * @return имя, в котором все точки заменены на подчёркивания
     */
    public static String safeStem(String fileName) {
        return fileName.replace('.', '_');
    }
}