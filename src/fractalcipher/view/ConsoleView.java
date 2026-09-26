package fractalcipher.view;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Минимальная консольная реализация — только чтобы показать,
 * что View не знает ничего про фракталы/карты, только про
 * ввод-вывод. Позже можно заменить на GUI, не трогая
 * Controller и Model.
 */
public class ConsoleView implements ImageView {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public BufferedImage requestInputImage() {
        System.out.print("Путь к изображению: ");
        String path = scanner.nextLine();
        try {
            return ImageIO.read(new File(path));
        } catch (IOException e) {
            showError("Не удалось прочитать файл: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String requestSourceName() {
        System.out.print("Источник (mandelbrot / julia / logistic / henon): ");
        return scanner.nextLine();
    }

    @Override
    public void showResult(BufferedImage result) {
        System.out.println("Готово. Результат: " + result);
    }

    @Override
    public void showError(String message) {
        System.out.println("[Ошибка] " + message);
    }
}