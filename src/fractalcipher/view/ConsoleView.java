package fractalcipher.view;

import fractalcipher.domain.Image;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class ConsoleView implements ImageView {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public Image requestInputImage() {
        System.out.print("Путь к изображению: ");
        String path = scanner.nextLine();
        File file = new File(path);
        try {
            return Image.loadFromFile(file.getName(), file.getName(), file);
        } catch (IOException e) {
            showError("Не удалось прочитать файл: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String requestMode() {
        System.out.print("Режим (encrypt / decrypt / benchmark): ");
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
    public void showResult(Image result) {
        System.out.println("Готово: " + result.getName()
                + " [" + result.getResolution() + ", " + result.getFileType()
                + ", ч/б: " + result.isBlackAndWhite() + "]");
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