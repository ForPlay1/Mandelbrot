package fractalcipher;

import fractalcipher.controller.CipherController;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.model.EncryptionKey;
import fractalcipher.model.ImageCipher;
import fractalcipher.model.ImageCipherImpl;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();
        ImageCipher cipher = new ImageCipherImpl(permutationFactory, diffusionFactory);
        CipherController controller = new CipherController(cipher);
        ImageView view = new ConsoleView();

        BufferedImage image = view.requestInputImage();
        if (image == null) return;

        String mode = view.requestMode();

        if ("benchmark".equalsIgnoreCase(mode)) {
            List<BenchmarkResult> results = controller.handleBenchmark(
                    image,
                    new ArrayList<>(permutationFactory.availableNames()),
                    new ArrayList<>(diffusionFactory.availableNames())
            );
            view.showBenchmarkResults(results);
            return;
        }

        String permutationName = view.requestPermutationName();
        String diffusionName = view.requestDiffusionName();
        EncryptionKey key = new EncryptionKey(
                permutationName, new HashMap<>(),
                diffusionName, new HashMap<>()
        );

        try {
            BufferedImage result = controller.handleEncrypt(image, key);
            view.showResult(result);
        } catch (UnsupportedOperationException e) {
            view.showError(e.getMessage());
        }
    }
}