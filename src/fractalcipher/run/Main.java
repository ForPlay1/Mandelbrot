package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Фабрики — тот же единый список стратегий, что и в предыдущей
        // версии. Никакого реестра классов-комбинаций больше нет:
        // FractalChaosCipher создаётся на лету с нужной парой стратегий.
        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();

        CipherController controller = new CipherController();
        ImageView view = new ConsoleView();

        Image image = view.requestInputImage();
        if (image == null) return;

        String mode = view.requestMode();

        if ("benchmark".equalsIgnoreCase(mode)) {
            List<BenchmarkResult> results = controller.handleBenchmark(permutationFactory, diffusionFactory, image);
            view.showBenchmarkResults(results);
            return;
        }

        String permutationName = view.requestPermutationName();
        String diffusionName = view.requestDiffusionName();

        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName)
        );

        try {
            Image result = "decrypt".equalsIgnoreCase(mode)
                    ? controller.handleDecrypt(cipher, image)
                    : controller.handleEncrypt(cipher, image);
            view.showResult(result);
        } catch (UnsupportedOperationException e) {
            view.showError(e.getMessage());
        }
    }
}