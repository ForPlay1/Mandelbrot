package fractalcipher.utils;

import fractalcipher.domain.Image;
import fractalcipher.model.KeyDerivation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CipherUtils {

    public static final String ENCRYPTED_SUFFIX = "_encrypted";
    public static final List<String> IMAGE_EXTENSIONS = Image.getImageExtensions();

    /**
     * Генерирует параметры для перестановки на основе мастер-сида.
     */
    public static Map<String, Double> buildPermParams(byte[] masterKey, String permName) {
        Map<String, Double> map = new HashMap<>();
        map.put("seed", (double) KeyDerivation.deriveLong(masterKey, "perm:" + permName));
        return map;
    }

    /**
     * Генерирует параметры для диффузии (включая S-боксы) на основе мастер-сида.
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
     * Проверяет, является ли файл картинкой, которую нужно зашифровать
     * (исключает уже зашифрованные файлы по суффиксу).
     */
    public static boolean isImageToEncrypt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext) && !baseName(fileName).endsWith(ENCRYPTED_SUFFIX);
    }

    /**
     * Возвращает имя файла без расширения.
     */
    public static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    /**
     * Безопасное «ядро» имени: точка расширения заменяется на подчёркивание.
     * Разные расширения → разные имена → нет коллизий.
     * Пример: "photo.png" → "photo_png", "photo.jpg" → "photo_jpg".
     */
    public static String safeStem(String fileName) {
        return fileName.replace('.', '_');
    }
}