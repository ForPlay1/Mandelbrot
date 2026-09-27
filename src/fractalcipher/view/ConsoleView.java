package fractalcipher.view;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

// Минимальная консольная реализация ImageView. Специально написана
// "бедно" (только System.out/System.in) — цель на этом этапе не
// удобный UI, а доказать, что View можно менять независимо от
// остального кода. Scanner создаётся один раз на весь объект (а не
// в каждом методе), иначе закрытие System.in между вызовами могло
// бы сломать повторный ввод.
public class ConsoleView implements ImageView {

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public BufferedImage requestInputImage() {
        System.out.print("Путь к изображению: ");
        String path = scanner.nextLine();
        try {
            // ImageIO.read сам определяет формат файла (png/jpg/...)
            // по содержимому, не по расширению.
            return ImageIO.read(new File(path));
        } catch (IOException e) {
            showError("Не удалось прочитать файл: " + e.getMessage());
            return null; // Main проверяет null и не идёт дальше, если чтение не удалось
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
        // Пока просто печатаем ссылку на объект BufferedImage —
        // сохранение в файл через ImageIO.write можно добавить
        // позже, когда решите, куда и в каком формате сохранять.
        System.out.println("Готово. Результат: ");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Результат");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new JLabel(new ImageIcon(result)));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    @Override
    public void showBenchmarkResults(List<?> results) {
        System.out.println("Результаты сравнения:");
        for (Object r : results) {
            // Вызывается BenchmarkResult.toString() — ConsoleView
            // не обязана знать, что там внутри этого объекта.
            System.out.println(r);
        }
    }

    @Override
    public void showError(String message) {
        System.out.println("[Ошибка] " + message);
    }
}