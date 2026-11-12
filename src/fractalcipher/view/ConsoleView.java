package fractalcipher.view;

import fractalcipher.domain.Image;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import javax.swing.*;

/**
 * Консольная реализация {@link ImageView}.
 * <p>
 * Используется в тестах и как fallback, когда JavaFX-интерфейс
 * недоступен. Вопросы задаются через {@link Scanner} поверх
 * {@link System#in}, ответы выводятся в {@code System.out}.
 * </p>
 * <p>
 * Для показа готового изображения открывает дополнительное окно Swing —
 * единственный способ без JavaFX наглядно показать результат.
 * </p>
 * <p>
 * <b>Не потокобезопасен</b>: предполагается, что вызывается из одного
 * потока (обычно — из worker-потока или теста).
 * </p>
 *
 * @author FractalCipher Team
 * @see ImageView
 * @see MainView
 */
public class ConsoleView implements ImageView {

    /** Источник пользовательского ввода. */
    private final Scanner scanner = new Scanner(System.in);

    /**
     * Запрашивает путь к изображению и пытается его загрузить.
     *
     * @return загруженное изображение или {@code null}, если файл
     *         не удалось прочитать
     */
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

    /**
     * Запрашивает путь к папке с изображениями.
     *
     * @return существующая папка или {@code null}, если путь
     *         некорректен (не папка / не существует)
     */
    @Override
    public File requestFolder() {
        System.out.print("Путь к папке с изображениями: ");
        String path = scanner.nextLine().trim();
        File dir = new File(path);
        if (!dir.isDirectory()) {
            showError("Это не папка или она не существует: " + path);
            return null;
        }
        return dir;
    }

    /**
     * Запрашивает режим работы.
     *
     * @return строка режима: {@code "encrypt"}, {@code "decrypt"},
     *         {@code "benchmark"}, {@code "bencrypt"} или {@code "bendecrypt"}
     */
    @Override
    public String requestMode() {
        System.out.print("Режим (encrypt / decrypt / benchmark / bencrypt / bendecrypt): ");
        return scanner.nextLine();
    }

    /**
     * Запрашивает имя источника перестановки.
     *
     * @return строковое имя (например, {@code "mandelbrot"})
     */
    @Override
    public String requestPermutationName() {
        System.out.print("Источник перестановки (mandelbrot / julia / cantor / burning ship): ");
        return scanner.nextLine();
    }

    /**
     * Запрашивает имя источника диффузии.
     *
     * @return строковое имя (например, {@code "logistic"})
     */
    @Override
    public String requestDiffusionName() {
        System.out.print("Источник диффузии (logistic / henon / tent / lorenz / rossler / arnold / null): ");
        return scanner.nextLine();
    }

    /**
     * Показывает результат: печатает метаданные в консоль и открывает
     * окно Swing с изображением.
     * <p>
     * Окно создаётся через {@link SwingUtilities#invokeLater} —
     * на Event Dispatch Thread, как того требует Swing.
     * </p>
     *
     * @param result изображение-результат
     */
    @Override
    public void showResult(Image result) {
        System.out.println("Готово: " + result.getName()
                + " [" + result.getResolution() + ", " + result.getFileType()
                + ", ч/б: " + result.isBlackAndWhite() + "]");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Результат");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new JLabel(new ImageIcon(result.getImage())));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /**
     * Печатает результаты бенчмарка в консоль.
     * <p>
     * Используется {@link Object#toString()} каждого элемента —
     * для {@link fractalcipher.metrics.BenchmarkResult} он уже
     * отформатирован наглядно.
     * </p>
     *
     * @param results список результатов
     */
    @Override
    public void showBenchmarkResults(List<?> results) {
        System.out.println("Результаты сравнения:");
        for (Object r : results) {
            System.out.println(r);
        }
    }

    /**
     * Печатает сообщение об ошибке.
     *
     * @param message текст ошибки
     */
    @Override
    public void showError(String message) {
        System.out.println("[Ошибка] " + message);
    }
}