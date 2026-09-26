package fractalcipher.model;

import java.awt.image.BufferedImage;

/**
 * Контракт шифра изображения. Не фиксирует, как именно
 * последовательность из ChaosSource превращается в изменение
 * пикселей (XOR по яркости? перестановка пикселей? и то, и то?)
 * — это отдельное архитектурное решение, ещё не принятое.
 */
public interface ImageCipher {
    BufferedImage encrypt(BufferedImage image, EncryptionKey key);
    BufferedImage decrypt(BufferedImage image, EncryptionKey key);
}