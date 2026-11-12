package fractalcipher.domain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Иммутабельное изображение с метаданными.
 * <p>
 * Все поля {@code final}: если нужно «изменить» изображение (например,
 * после шифрования), создаётся новый объект {@code Image}, а не правится
 * существующий. Это безопаснее — нет риска, что {@code imageInput}
 * изменится прямо во время шифрования.
 * </p>
 * <p>
 * Поля {@link #resolution} и {@link #blackAndWhite} не передаются снаружи,
 * а вычисляются из самого изображения — нельзя случайно указать
 * неверное разрешение, не совпадающее с реальным.
 * </p>
 *
 * @author FractalCipher Team
 */
public class Image {
    /** Уникальный идентификатор изображения (например, имя файла). */
    private final String id;

    /** Человекочитаемое имя (например, имя файла с расширением). */
    private final String name;

    /** Сами пиксельные данные. */
    private final BufferedImage image;

    /** Размер файла в байтах. */
    private final long size;

    /** Расширение файла без точки ({@code "png"}, {@code "jpg"} и т.д.). */
    private final String fileType;

    /** Разрешение в формате {@code "ШИРИНАxВЫСОТА"}. */
    private final String resolution;

    /** Флаг «изображение в оттенках серого / чёрно-белое». */
    private final boolean blackAndWhite;

    /**
     * Создаёт изображение с полными метаданными.
     *
     * @param id       идентификатор
     * @param name     имя
     * @param image    пиксельные данные
     * @param size     размер в байтах
     * @param fileType тип файла (расширение без точки)
     */
    public Image(String id, String name, BufferedImage image, long size, String fileType) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.size = size;
        this.fileType = fileType;
        // resolution и blackAndWhite не передаются снаружи, а вычисляются
        // из самой картинки — так нельзя случайно указать неверное
        // разрешение, не совпадающее с реальным изображением.
        this.resolution = image.getWidth() + "x" + image.getHeight();
        this.blackAndWhite = detectBlackAndWhite(image);
    }

    /**
     * Фабричный метод: читает файл с диска и заполняет все метаданные
     * ({@code size} — из {@link File#length()}, {@code fileType} — из
     * расширения, {@code resolution} и {@code blackAndWhite} — из
     * самого изображения).
     *
     * @param id   идентификатор
     * @param name имя
     * @param file файл с изображением
     * @return загруженное изображение
     * @throws IOException если файл не найден или не распознан как изображение
     */
    public static Image loadFromFile(String id, String name, File file) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(file);
        if (bufferedImage == null) {
            throw new IOException("Файл не распознан как изображение: " + file.getName());
        }
        return new Image(id, name, bufferedImage, file.length(), detectFileType(file.getName()));
    }

    /**
     * Извлекает расширение файла без точки.
     *
     * @param fileName имя файла
     * @return расширение в нижнем регистре или {@code "unknown"}
     */
    private static String detectFileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "unknown" : fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * Максимально допустимое отклонение между каналами R/G/B, при котором
     * пиксель ещё считается «серым». 8 из 255 — примерно 3%; глаз такого
     * различия не замечает.
     */
    private static final int CHANNEL_THRESHOLD = 8;

    /**
     * Доля «цветных» пикселей, которую допускаем. 1% — достаточно, чтобы
     * пережить JPEG-артефакты, но не пропустить реально цветное изображение.
     */
    private static final double COLORFUL_PIXEL_RATIO = 0.01;

    /**
     * Эвристика: если у КАЖДОГО непрозрачного пикселя R≈G≈B,
     * изображение считается чёрно-белым (точнее — в оттенках серого).
     * <p>
     * Сложность O(width × height). На очень больших изображениях это
     * заметно; при необходимости можно проверять случайную выборку.
     * </p>
     *
     * @param image изображение
     * @return {@code true}, если изображение в оттенках серого
     */
    private static boolean detectBlackAndWhite(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;

        // Сколько пикселей МОЖЕТ быть цветными, прежде чем признаем
        // изображение цветным. Считаем один раз.
        int allowedColorful = (int) (totalPixels * COLORFUL_PIXEL_RATIO);
        int colorfulPixels = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);

                // === НОВОЕ: пропускаем полностью прозрачные пиксели ===
                // У них нет видимого цвета, R/G/B могут быть любыми.
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // === НОВОЕ: порог на максимальное отклонение ===
                int diffRG = Math.abs(r - g);
                int diffGB = Math.abs(g - b);
                int diffRB = Math.abs(r - b);
                int maxDiff = Math.max(diffRG, Math.max(diffGB, diffRB));

                if (maxDiff > CHANNEL_THRESHOLD) {
                    colorfulPixels++;

                    // === НОВОЕ: ранний выход при превышении лимита ===
                    // Если цветных пикселей уже больше допустимого —
                    // нет смысла продолжать, экономим время.
                    if (colorfulPixels > allowedColorful) return false;
                }
            }
        }
        return true;
    }
    /**
     * @return список поддерживаемых приложением расширений изображений
     */
    public static List<String> getImageExtensions() {
        return List.of("png", "jpg", "jpeg", "bmp", "gif");
    }

    /** @return идентификатор изображения */
    public String getId() { return id; }

    /** @return человекочитаемое имя */
    public String getName() { return name; }

    /** @return пиксельные данные */
    public BufferedImage getImage() { return image; }

    /** @return размер в байтах */
    public long getSize() { return size; }

    /** @return тип файла (расширение без точки) */
    public String getFileType() { return fileType; }

    /** @return разрешение в формате {@code "WxH"} */
    public String getResolution() { return resolution; }

    /** @return {@code true}, если изображение в оттенках серого */
    public boolean isBlackAndWhite() { return blackAndWhite; }
}