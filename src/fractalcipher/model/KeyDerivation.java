package fractalcipher.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Деривация параметров из мастер-seed.
 * Доменная сепарация (domain separation) гарантирует, что разные
 * источники (перестановка/диффузия) получат НЕЗАВИСИМЫЕ параметры
 * из одного и того же мастер-seed.
 */
public class KeyDerivation {

    // Детерминированный long из мастер-seed и домена
    public static long deriveLong(long masterSeed, String domain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(longToBytes(masterSeed));
            md.update(domain.getBytes());
            byte[] hash = md.digest();

            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (hash[i] & 0xFF);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Детерминированный double в (0, 1) — для x0 логистической карты
    public static double deriveDouble(long masterSeed, String domain) {
        long v = deriveLong(masterSeed, domain);
        double d = (Math.abs(v) % 1_000_000_000L) / 1_000_000_000.0;
        if (d <= 0.0) d = 0.1;
        if (d >= 1.0) d = 0.9;
        return d;
    }

    // Детерминированный S-бокс (перестановка 0..255) — для нелинейности
    public static int[] deriveSBox(long masterSeed, String domain) {
        long seed = deriveLong(masterSeed, domain);
        int[] sbox = new int[256];
        for (int i = 0; i < 256; i++) sbox[i] = i;
        Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = sbox[i];
            sbox[i] = sbox[j];
            sbox[j] = tmp;
        }
        return sbox;
    }

    private static byte[] longToBytes(long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) (v & 0xFF);
            v >>= 8;
        }
        return b;
    }
}