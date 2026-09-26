package fractalcipher.view;

import java.awt.image.BufferedImage;

public interface ImageView {
    BufferedImage requestInputImage();
    String requestMode(); // "encrypt" или "benchmark"
    String requestPermutationName();
    String requestDiffusionName();
    void showResult(BufferedImage result);
    void showBenchmarkResults(java.util.List<?> results);
    void showError(String message);
}