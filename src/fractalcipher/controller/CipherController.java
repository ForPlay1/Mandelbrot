package fractalcipher.controller;

import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.metrics.BenchmarkRunner;
import fractalcipher.model.EncryptionKey;
import fractalcipher.model.ImageCipher;

import java.awt.image.BufferedImage;
import java.util.List;

// Controller в терминах MVC: единственная задача — принять запрос от
// View и передать его в Model (сюда — в ImageCipher), вернуть результат
// обратно. Ни одной строчки про фракталы, пиксели или XOR здесь нет —
// и не должно быть, иначе это уже не Controller, а часть Model.
public class CipherController {

    private final ImageCipher cipher;

    public CipherController(ImageCipher cipher) {
        this.cipher = cipher;
    }

    public BufferedImage handleEncrypt(BufferedImage image, EncryptionKey key) {
        return cipher.encrypt(image, key);
    }

    public BufferedImage handleDecrypt(BufferedImage image, EncryptionKey key) {
        return cipher.decrypt(image, key);
    }

    // Тоже просто передаёт вызов дальше — саму логику перебора
    // комбинаций и подсчёта метрик Controller не делает сам,
    // этим занимается BenchmarkRunner (это уже вычислительная логика,
    // то есть тоже часть Model-слоя по смыслу, а не Controller).
    public List<BenchmarkResult> handleBenchmark(BufferedImage image, List<String> permutationNames,
                                                 List<String> diffusionNames) {
        return BenchmarkRunner.run(cipher, image, permutationNames, diffusionNames);
    }
}