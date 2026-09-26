package fractalcipher.controller;

import fractalcipher.model.EncryptionKey;
import fractalcipher.model.ImageCipher;

import java.awt.image.BufferedImage;

/**
 * Controller: не знает, какой конкретно источник выбран
 * (Мандельброт, логистическая карта и т.д.) — просто передаёт
 * запрос в Model и результат обратно в View.
 */
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
}