package fractalcipher.view;

import java.awt.image.BufferedImage;

// Интерфейс View. Смысл в том, что Controller зависит только от
// ЭТОГО интерфейса, а не от ConsoleView напрямую — значит, позже
// можно написать GuiView (Swing/JavaFX) и подставить её в Main,
// вообще не трогая Controller и Model.
public interface ImageView {
    BufferedImage requestInputImage();

    // "encrypt" или "benchmark" — какой режим работы выбрал пользователь
    String requestMode();

    String requestPermutationName();
    String requestDiffusionName();

    void showResult(BufferedImage result);

    // List<?> — сознательно не List<BenchmarkResult>, чтобы View
    // (по духу MVC) не обязательно знала о существовании класса
    // BenchmarkResult из пакета metrics; ей достаточно уметь
    // напечатать что угодно через toString().
    void showBenchmarkResults(java.util.List<?> results);

    void showError(String message);
}