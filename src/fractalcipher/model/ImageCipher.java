package fractalcipher.model;

import java.awt.image.BufferedImage;

// Контракт шифра изображения. Специально максимально простой —
// фиксирует только вход/выход (картинка + ключ -> картинка), а
// КАК именно происходит шифрование, целиком скрыто в реализации
// (ImageCipherImpl). Это то, что даёт Controller'у возможность
// работать с шифром, вообще не зная, что внутри перестановка и XOR.
public interface ImageCipher {
    BufferedImage encrypt(BufferedImage image, EncryptionKey key);
    BufferedImage decrypt(BufferedImage image, EncryptionKey key);
}