package fractalcipher.view;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

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
    public String requestMode() {
        System.out.print("Режим (encrypt / benchmark): ");
        return scanner.nextLine();
    }

    @Override
    public String requestPermutationName() {
        System.out.print("Источник перестановки (mandelbrot / julia / cantor): ");
        return scanner.nextLine();
    }

    @Override
    public String requestDiffusionName() {
        System.out.print("Источник диффузии (logistic / henon): ");
        return scanner.nextLine();
    }

    @Override
    public void showResult(BufferedImage result) {
        System.out.println("Готово. Результат: " + result);
    }

    @Override
    public void showBenchmarkResults(List<?> results) {
        System.out.println("Результаты сравнения:");
        for (Object r : results) {
            System.out.println(r);
        }
    }

    @Override
    public void showError(String message) {
        System.out.println("[Ошибка] " + message);
    }
}