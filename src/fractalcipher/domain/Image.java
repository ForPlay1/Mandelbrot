package fractalcipher.domain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Ровно те поля, которые вы обсудили с преподавателем:
 * id, название, сама картинка, размер, тип файла, разрешение,
 * метка "чёрно-белое или нет".
 *
 * Класс иммутабельный (все поля final) — если нужно "изменить"
 * изображение (например, после шифрования), создаётся новый объект
 * Image, а не правится существующий. Это безопаснее: нет риска,
 * что imageInput случайно изменится прямо во время шифрования.
 */
public class Image {
    private final String id;
    private final String name;
    private final BufferedImage image;
    private final long size;          // в байтах
    private final String fileType;    // "png", "jpg" и т.д.
    private final String resolution;  // "ШИРИНАxВЫСОТА"
    private final boolean blackAndWhite;

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
     * Фабричный метод: читает файл с диска и сам заполняет все
     * метаданные (size — из file.length(), fileType — из расширения,
     * resolution/blackAndWhite — из самого изображения). Избавляет
     * от необходимости собирать Image вручную в каждом месте, где
     * нужно загрузить картинку из файла.
     */
    public static Image loadFromFile(String id, String name, File file) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(file);
        if (bufferedImage == null) {
            throw new IOException("Файл не распознан как изображение: " + file.getName());
        }
        return new Image(id, name, bufferedImage, file.length(), detectFileType(file.getName()));
    }

    private static String detectFileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "unknown" : fileName.substring(dot + 1).toLowerCase();
    }

    // Порог на пиксель: максимально допустимое отклонение между каналами.
    // 8 из 255 — примерно 3%, человеческий глаз на таком уровне
    // различия не замечает. Для более строгого определения — 4, для
    // более мягкого — 16.
    private static final int CHANNEL_THRESHOLD = 8;

    // Доля «цветных» пикселей, которую допускаем. 1% — достаточно,
    // чтобы пережить JPEG-артефакты и шум сжатия, но не пропустить
    // реально цветное изображение.
    private static final double COLORFUL_PIXEL_RATIO = 0.01;

    /**
     * Эвристика: если у КАЖДОГО пикселя R=G=B, считаем изображение
     * чёрно-белым (точнее — оттенками серого; формально "чёрно-белое"
     * 1-битное изображение тоже под это подходит, там R=G=B всегда
     * равны либо 0, либо 255).
     *
     * Внимание: это O(width*height) — на больших изображениях
     * конструктор Image будет заметно медленнее. Если это станет
     * проблемой, можно проверять не все пиксели, а случайную выборку.
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

    public String getId() { return id; }
    public String getName() { return name; }
    public BufferedImage getImage() { return image; }
    public long getSize() { return size; }
    public String getFileType() { return fileType; }
    public String getResolution() { return resolution; }
    public boolean isBlackAndWhite() { return blackAndWhite; }
}