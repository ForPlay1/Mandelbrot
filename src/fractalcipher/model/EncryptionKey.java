package fractalcipher.model;

import java.util.Map;

/**
 * Ключ теперь описывает обе части гибридной схемы:
 * какой фрактал даёт перестановку и какая карта даёт диффузию,
 * плюс параметры каждого из них.
 */
public class EncryptionKey {
    public final String permutationSourceName;
    public final Map<String, Double> permutationParams;
    public final String diffusionSourceName;
    public final Map<String, Double> diffusionParams;

    public EncryptionKey(String permutationSourceName, Map<String, Double> permutationParams,
                         String diffusionSourceName, Map<String, Double> diffusionParams) {
        this.permutationSourceName = permutationSourceName;
        this.permutationParams = permutationParams;
        this.diffusionSourceName = diffusionSourceName;
        this.diffusionParams = diffusionParams;
    }
}