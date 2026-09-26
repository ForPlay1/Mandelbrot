package fractalcipher.view;

import java.awt.image.BufferedImage;

/**
 * Интерфейс View, чтобы Controller не зависел от того,
 * консоль это сейчас или GUI (Swing/JavaFX) в будущем.
 */
public interface ImageView {
    BufferedImage requestInputImage();
    String requestSourceName();
    void showResult(BufferedImage result);
    void showError(String message);
}