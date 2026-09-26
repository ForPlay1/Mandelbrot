package fractalcipher.model;

import java.awt.image.BufferedImage;

/**
 * Заглушка. Умеет только получить нужный ChaosSource через фабрику —
 * само наложение последовательности на пиксели ещё не решено
 * (и, скорее всего, будет отличаться для разных источников,
 * поэтому пока не факт, что один класс на всё — финальный дизайн).
 */
public class ImageCipherImpl implements ImageCipher {

    private final ChaosSourceFactory sourceFactory;

    public ImageCipherImpl(ChaosSourceFactory sourceFactory) {
        this.sourceFactory = sourceFactory;
    }

    @Override
    public BufferedImage encrypt(BufferedImage image, EncryptionKey key) {
        ChaosSource source = sourceFactory.get(key.sourceName);
        // TODO: source.generate(...) -> наложить на пиксели image
        throw new UnsupportedOperationException("Схема шифрования пикселей ещё не выбрана");
    }

    @Override
    public BufferedImage decrypt(BufferedImage image, EncryptionKey key) {
        // TODO: обратное преобразование
        throw new UnsupportedOperationException("Схема дешифрования пикселей ещё не выбрана");
    }
}