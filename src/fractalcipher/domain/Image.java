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
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r != g || g != b) return false;
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