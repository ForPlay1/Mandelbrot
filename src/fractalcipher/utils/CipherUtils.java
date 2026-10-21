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
    public static Map<String, Double> buildPermParams(long masterSeed, String permName) {
        Map<String, Double> map = new HashMap<>();
        map.put("seed", (double) KeyDerivation.deriveLong(masterSeed, "perm:" + permName));
        return map;
    }

    /**
     * Генерирует параметры для диффузии (включая S-боксы) на основе мастер-сида.
     */
    public static Map<String, Double> buildDiffParams(long masterSeed, String diffName) {
        Map<String, Double> map = new HashMap<>();
        map.put("seed", (double) KeyDerivation.deriveLong(masterSeed, "diff:" + diffName));
        map.put("x0", KeyDerivation.deriveDouble(masterSeed, "x0:" + diffName));
        map.put("sboxSeed", (double) KeyDerivation.deriveLong(masterSeed, "sbox:" + diffName));
        map.put("sboxCbc1", (double) KeyDerivation.deriveLong(masterSeed, "cbc1:" + diffName));
        map.put("sboxCbc2", (double) KeyDerivation.deriveLong(masterSeed, "cbc2:" + diffName));
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
}