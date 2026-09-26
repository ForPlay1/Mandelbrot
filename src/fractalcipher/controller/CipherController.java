package fractalcipher.controller;

import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.metrics.BenchmarkRunner;
import fractalcipher.model.EncryptionKey;
import fractalcipher.model.ImageCipher;

import java.awt.image.BufferedImage;
import java.util.List;

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

    public List<BenchmarkResult> handleBenchmark(BufferedImage image, List<String> permutationNames,
                                                 List<String> diffusionNames) {
        return BenchmarkRunner.run(cipher, image, permutationNames, diffusionNames);
    }
}