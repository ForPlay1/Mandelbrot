package fractalcipher.view;

import fractalcipher.domain.Image;

import java.io.File;
import java.util.List;

public interface ImageView {
    Image requestInputImage();
    File requestFolder();              // путь к папке с изображениями (для bencrypt), null если некорректный
    String requestMode();              // "encrypt" / "decrypt" / "benchmark" / "bencrypt"
    String requestPermutationName();   // "mandelbrot" / "julia" / "cantor"
    String requestDiffusionName();     // "logistic" / "henon"
    void showResult(Image result);
    void showBenchmarkResults(List<?> results);
    void showError(String message);
}