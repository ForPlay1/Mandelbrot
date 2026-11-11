package fractalcipher.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Утилита для деривации (вывода) параметров из мастер-сида.
 * <p>
 * <b>Доменная сепарация (domain separation)</b> — ключевой принцип этого класса.
 * Он гарантирует, что разные источники (перестановка, диффузия, S-боксы)
 * получат <i>НЕЗАВИСИМЫЕ</i> параметры из одного и того же мастер-сида.
 * Без этого злоумышленник мог бы найти корреляции между разными этапами шифрования.
 * </p>
 */
public class KeyDerivation {

    /**
     * Детерминированно вычисляет 64-битное число из мастер-сида и домена.
     * <p>
     * Использует криптографическую хеш-функцию SHA-256 как генератор
     * псевдослучайных чисел (PRF). Входные данные: байты мастер-сида + байты домена.
     * </p>
     *
     * @param masterKey Главное зерно ключа.
     * @param domain     Строка-разделитель (например, "perm:mandelbrot").
     * @return Псевдослучайное 64-битное число.
     */
    public static long deriveLong(byte[] masterKey, String domain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(masterKey);
            md.update(domain.getBytes()); // Добавляем домен (разделитель)
            byte[] hash = md.digest();

            // Берем первые 8 байт хеша и собираем из них long
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (hash[i] & 0xFF);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 гарантированно есть в любой стандартной JVM
            throw new RuntimeException(e);
        }
    }

    /**
     * Детерминированно вычисляет число типа {@code double} в интервале (0, 1).
     * Используется для задания начальных значений (например, x0) хаотическим картам.
     *
     * @param masterKey Главное зерно ключа.
     * @param domain     Строка-разделитель.
     * @return Число от 0.1 до 0.9 (защита от краевых значений).
     */
    public static double deriveDouble(byte[] masterKey, String domain) {
        long v = deriveLong(masterKey, domain);
        // Преобразуем в [0, 1) и сдвигаем в безопасный диапазон
        double d = (Math.abs(v) % 1_000_000_000L) / 1_000_000_000.0;
        if (d <= 0.0) d = 0.1;
        if (d >= 1.0) d = 0.9;
        return d;
    }

    /**
     * Генерирует S-бокс (таблицу замен 0..255) на основе мастер-сида.
     * <p>
     * Использует алгоритм перемешивания Фишера-Йетса (Fisher-Yates shuffle),
     * инициализированный псевдослучайным числом.
     * </p>
     *
     * @param masterKey Главное зерно ключа.
     * @param domain     Строка-разделитель (например, "sbox:cbc1").
     * @return Массив из 256 уникальных чисел (перестановка 0..255).
     */
    public static int[] deriveSBox(byte[] masterKey, String domain) {
        long seed = deriveLong(masterKey, domain);
        int[] sbox = new int[256];
        // Инициализация: 0, 1, 2, ..., 255
        for (int i = 0; i < 256; i++) sbox[i] = i;
        // Перемешивание
        Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = sbox[i];
            sbox[i] = sbox[j];
            sbox[j] = tmp;
        }
        return sbox;
    }

}