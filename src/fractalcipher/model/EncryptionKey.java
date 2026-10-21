package fractalcipher.model;

import java.io.Serializable;

/**
 * Ключ шифрования изображения.
 * Хранит только мастер-сид и названия выбранных алгоритмов.
 * Все остальные параметры (x0, sboxSeed и т.д.) будут
 * заново вычислены из masterSeed с помощью KeyDerivation.
 */
public class EncryptionKey implements Serializable {
    private static final long serialVersionUID = 1L;

    public final long masterSeed;
    public final String permutationSourceName;
    public final String diffusionSourceName;

    public EncryptionKey(long masterSeed, String permutationSourceName, String diffusionSourceName) {
        this.masterSeed = masterSeed;
        this.permutationSourceName = permutationSourceName;
        this.diffusionSourceName = diffusionSourceName;
    }
}