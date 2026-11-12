package fractalcipher.view;

import fractalcipher.domain.Image;

import java.io.File;
import java.util.List;

/**
 * Контракт View: всё, что контроллеру нужно от интерфейса.
 * <p>
 * Абстрагирует конкретную реализацию (консоль, JavaFX, тест-заглушка),
 * чтобы {@link fractalcipher.controller.CipherController} не зависел
 * от UI. Достаточно реализовать этот интерфейс — контроллер заработает
 * с любой реализацией.
 * </p>
 * <p>
 * Соответствие операциям контроллера:
 * </p>
 * <ul>
 *   <li>{@link #requestInputImage()} — режимы одиночного шифрования/расшифровки;</li>
 *   <li>{@link #requestFolder()} — пакетные режимы и бенчмарк на папке;</li>
 *   <li>{@link #requestMode()} — выбор операции;</li>
 *   <li>{@link #requestPermutationName()}, {@link #requestDiffusionName()} —
 *       параметры шифрования (в JavaFX-версии заменены на кнопки);</li>
 *   <li>{@link #showResult}, {@link #showBenchmarkResults}, {@link #showError} —
 *       обратная связь.</li>
 * </ul>
 *
 * @author FractalCipher Team
 * @see ConsoleView
 * @see fractalcipher.view.MainView
 */
public interface ImageView {
    /**
     * Запрашивает входное изображение (одиночный режим).
     *
     * @return изображение или {@code null}, если пользователь
     *         отменил ввод или файл не читается
     */
    Image requestInputImage();

    /**
     * Запрашивает папку с изображениями (пакетный режим или бенчмарк).
     *
     * @return существующая папка или {@code null}, если путь некорректен
     */
    File requestFolder();

    /**
     * Запрашивает режим работы.
     *
     * @return одно из значений {@code "encrypt"}, {@code "decrypt"},
     *         {@code "benchmark"}, {@code "bencrypt"}, {@code "bendecrypt"}
     */
    String requestMode();

    /**
     * Запрашивает имя источника перестановки.
     *
     * @return строковое имя (например, {@code "mandelbrot"})
     */
    String requestPermutationName();

    /**
     * Запрашивает имя источника диффузии.
     *
     * @return строковое имя (например, {@code "logistic"})
     */
    String requestDiffusionName();

    /**
     * Показывает результат одиночной операции.
     *
     * @param result изображение-результат
     */
    void showResult(Image result);

    /**
     * Показывает результаты бенчмарка.
     *
     * @param results список {@link fractalcipher.metrics.BenchmarkResult}
     *                (или других объектов — зависит от реализации)
     */
    void showBenchmarkResults(List<?> results);

    /**
     * Показывает сообщение об ошибке.
     *
     * @param message текст ошибки (безопасный для пользователя)
     */
    void showError(String message);
}