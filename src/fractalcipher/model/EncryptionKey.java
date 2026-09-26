package fractalcipher.model;

import java.util.Map;

/**
 * Ключ шифрования изображения: имя источника (какой фрактал/карта)
 * плюс его параметры. Одна и та же структура ключа подходит
 * для любого ChaosSource — конкретные params каждый источник
 * трактует по-своему.
 */
public class EncryptionKey {
    public final String sourceName;
    public final Map<String, Double> params;

    public EncryptionKey(String sourceName, Map<String, Double> params) {
        this.sourceName = sourceName;
        this.params = params;
    }
}