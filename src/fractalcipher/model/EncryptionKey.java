package fractalcipher.model;

import java.util.Map;

// Ключ шифрования изображения. Так как схема гибридная (фрактал +
// хаотическая карта), ключ хранит выбор и параметры ОБЕИХ частей —
// без этого нельзя ни зашифровать, ни расшифровать одинаково.
public class EncryptionKey {

    // Имя источника перестановки, например "mandelbrot" — должно
    // совпадать с getName() зарегистрированного PermutationSource.
    public final String permutationSourceName;

    // Параметры именно для этого источника (например reMin/reMax для
    // Мандельброта). Если оставить пустую Map — источник возьмёт
    // значения по умолчанию (см. getOrDefault в каждом источнике).
    public final Map<String, Double> permutationParams;

    // То же самое, но для источника диффузии, например "logistic".
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