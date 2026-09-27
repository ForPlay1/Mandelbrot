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

// Точка входа. Единственное место во всём проекте, где создаются
// конкретные классы (new ConsoleView(), new ImageCipherImpl(...) и
// т.д.) и связываются друг с другом — это называется "композиционный
// корень" (composition root). Все остальные классы получают уже
// готовые зависимости через конструктор и не создают их сами.
public class Main {
    public static void main(String[] args) {
        // Фабрики создаются один раз здесь и передаются дальше —
        // именно поэтому у ImageCipherImpl, Controller'а и BenchmarkRunner'а
        // единый список зарегистрированных источников, а не у каждого свой.
        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();

        ImageCipher cipher = new ImageCipherImpl(permutationFactory, diffusionFactory);
        CipherController controller = new CipherController(cipher);
        ImageView view = new ConsoleView();

        BufferedImage image = view.requestInputImage();
        if (image == null) return; // чтение не удалось, ошибку уже показали внутри requestInputImage

        String mode = view.requestMode();

        if ("benchmark".equalsIgnoreCase(mode)) {
            // В режиме бенчмарка НЕ спрашиваем конкретный источник —
            // берём вообще все зарегистрированные (availableNames())
            // и передаём в BenchmarkRunner через Controller.
            List<BenchmarkResult> results = controller.handleBenchmark(
                    image,
                    new ArrayList<>(permutationFactory.availableNames()),
                    new ArrayList<>(diffusionFactory.availableNames())
            );
            view.showBenchmarkResults(results);
            return; // на бенчмарке работа закончена, дальше идти незачем
        }

        // Обычный режим — пользователь сам выбирает ровно одну пару источников.
        String permutationName = view.requestPermutationName();
        String diffusionName = view.requestDiffusionName();

        // new HashMap<>() для параметров — источники используют свои
        // значения по умолчанию; если бы UI позволял вводить конкретные
        // r/x0/reMin и т.д., они добавлялись бы в эти Map перед созданием ключа.
        EncryptionKey key = new EncryptionKey(
                permutationName, new HashMap<>(),
                diffusionName, new HashMap<>()
        );

        try {
            BufferedImage result = controller.handleEncrypt(image, key);
            view.showResult(result);
        } catch (UnsupportedOperationException e) {
            // Ловим здесь, а не глубже внутри Model, потому что именно
            // View должна решать, КАК показать ошибку пользователю —
            // Model просто кидает исключение и не знает про консоль/GUI.
            view.showError(e.getMessage());
        }
    }
}