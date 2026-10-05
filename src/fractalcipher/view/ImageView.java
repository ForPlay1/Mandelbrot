package fractalcipher.view;

import fractalcipher.domain.Image;

import java.util.List;

public interface ImageView {
    Image requestInputImage();
    String requestMode();              // "encrypt" / "decrypt" / "benchmark"
    String requestPermutationName();   // "mandelbrot" / "julia" / "cantor"
    String requestDiffusionName();     // "logistic" / "henon"
    void showResult(Image result);
    void showBenchmarkResults(List<?> results);
    void showError(String message);
}