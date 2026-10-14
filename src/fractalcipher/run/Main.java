package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.exception.ErrorHandler;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Main {

    // Суффикс для зашифрованных файлов; по нему же при повторном запуске
    // пропускаем уже обработанные картинки
    private static final String ENCRYPTED_SUFFIX = "_encrypted";
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "bmp", "gif");

    public static void main(String[] args) {
        // === ЦЕНТРАЛИЗОВАННАЯ ОБРАБОТКА ОШИБОК ===
        // Единственное место с глобальным try-catch.
        // Все исключения из run() попадают сюда и обрабатываются
        // через ErrorHandler — единообразно, безопасно, с логированием.
        try {
            run();
        } catch (Throwable t) {
            String safe = ErrorHandler.handle(t);
            System.err.println(safe);
            System.exit(1);
        }
    }

    /**
     * Вся бизнес-логика приложения. Исключения НЕ обрабатываются здесь —
     * они пробрасываются наверх, в глобальный перехватчик.
     */
    private static void run() {
        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();

        CipherController controller = new CipherController();
        ImageView view = new ConsoleView();

        // Режим спрашиваем первым: для bencrypt вместо одной картинки нужна папка
        String mode = view.requestMode();

        // === ПАКЕТНОЕ ШИФРОВАНИЕ ПАПКИ ===
        if ("bencrypt".equalsIgnoreCase(mode)) {
            runBatchEncrypt(view, controller, permutationFactory, diffusionFactory);
            return;
        }

        Image image = view.requestInputImage();
        if (image == null) return;

        // === БЕНЧМАРК: три серии ===
        if ("benchmark".equalsIgnoreCase(mode)) {
            runBenchmark(view, controller, permutationFactory, diffusionFactory, image);
            return;
        }

        // === ОДИНОЧНОЕ ШИФРОВАНИЕ / РАСШИФРОВКА ===
        runSingle(view, controller, permutationFactory, diffusionFactory, image, mode);
    }

    // === БЕНЧМАРК: три серии ===
    private static void runBenchmark(ImageView view,
                                     CipherController controller,
                                     PermutationSourceFactory permutationFactory,
                                     DiffusionSourceFactory diffusionFactory,
                                     Image image) {

        // --- СЕРИЯ 1: только перестановка ---
        System.out.println("\n=== СЕРИЯ 1: ТОЛЬКО ПЕРЕСТАНОВКА ===");
        System.out.println("Сравнение фракталов по разрушению пространственных связей.");
        System.out.println("NPCR/UACI = 0 (значения пикселей не меняются),");
        System.out.println("смотрите на корреляцию — чем ближе к 0, тем лучше.\n");
        List<BenchmarkResult> permOnly =
                controller.handleBenchmarkPermutationOnly(permutationFactory, image);
        view.showBenchmarkResults(permOnly);

        // --- СЕРИЯ 2: только диффузия ---
        System.out.println("\n=== СЕРИЯ 2: ТОЛЬКО ДИФФУЗИЯ ===");
        System.out.println("Сравнение хаотических карт по NPCR/UACI/энтропии.");
        System.out.println("Перестановка = identity, CBC выключен.\n");
        List<BenchmarkResult> diffOnly =
                controller.handleBenchmarkDiffusionOnly(diffusionFactory, image);
        view.showBenchmarkResults(diffOnly);

        // --- СЕРИЯ 3: полная схема ---
        System.out.println("\n=== СЕРИЯ 3: ПОЛНАЯ СХЕМА ===");
        System.out.println("Итоговая криптостойкость + скорость.");
        System.out.println("CBC с S-боксами включён — метрики определяются им.\n");
        List<BenchmarkResult> full =
                controller.handleBenchmarkFull(permutationFactory, diffusionFactory, image);
        view.showBenchmarkResults(full);
    }

    // === ОДИНОЧНОЕ ШИФРОВАНИЕ / РАСШИФРОВКА ===
    private static void runSingle(ImageView view,
                                  CipherController controller,
                                  PermutationSourceFactory permutationFactory,
                                  DiffusionSourceFactory diffusionFactory,
                                  Image image,
                                  String mode) {

        String permutationName = view.requestPermutationName();
        String diffusionName = view.requestDiffusionName();

        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName)
        );

        Image result = "decrypt".equalsIgnoreCase(mode)
                ? controller.handleDecrypt(cipher, image)
                : controller.handleEncrypt(cipher, image);
        view.showResult(result);
    }

    // Шифрует все изображения из папки выбранным шифром. Результаты кладутся
    // в ту же папку: <имя>_encrypted.png. Всегда PNG — он без потерь; JPEG
    // испортил бы зашифрованные пиксели и расшифровка стала бы невозможной.
    private static void runBatchEncrypt(ImageView view,
                                        CipherController controller,
                                        PermutationSourceFactory permutationFactory,
                                        DiffusionSourceFactory diffusionFactory) {
        File dir = view.requestFolder();
        if (dir == null) return;

        File[] files = dir.listFiles(f -> f.isFile() && isImageToEncrypt(f.getName()));
        if (files == null || files.length == 0) {
            view.showError("В папке нет изображений для шифрования (png, jpg, jpeg, bmp, gif)");
            return;
        }
        Arrays.sort(files);

        String permutationName = view.requestPermutationName();
        String diffusionName = view.requestDiffusionName();

        FractalChaosCipher cipher;
        try {
            cipher = new FractalChaosCipher(
                    "c1", permutationName + "+" + diffusionName,
                    permutationFactory.get(permutationName),
                    diffusionFactory.get(diffusionName)
            );
        } catch (IllegalArgumentException e) {
            // Локальная обработка: неверное имя источника. Показываем пользователю.
            view.showError(ErrorHandler.handle(e));
            return;
        }

        System.out.println("Найдено файлов: " + files.length);
        int ok = 0;
        int failed = 0;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String prefix = "[" + (i + 1) + "/" + files.length + "] " + file.getName() + ": ";
            try {
                long start = System.currentTimeMillis();

                Image input = Image.loadFromFile(file.getName(), file.getName(), file);
                Image result = controller.handleEncrypt(cipher, input);

                File target = new File(dir, baseName(file.getName()) + ENCRYPTED_SUFFIX + ".png");
                if (!ImageIO.write(result.getImage(), "png", target)) {
                    throw new IOException("не удалось записать PNG");
                }

                System.out.println(prefix + "OK -> " + target.getName()
                        + " (" + (System.currentTimeMillis() - start) + " мс)");
                ok++;
            } catch (Throwable t) {
                // Локальная обработка: одна битая картинка не должна
                // останавливать всю папку. Логирование — через ErrorHandler,
                // продолжение — со следующего файла.
                String safe = ErrorHandler.handle(t);
                view.showError(prefix + safe);
                failed++;
            }
        }

        System.out.println("Готово. Успешно: " + ok + ", с ошибкой: " + failed);
    }

    // Картинка с поддерживаемым расширением, которая ещё не является результатом шифрования
    private static boolean isImageToEncrypt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext) && !baseName(fileName).endsWith(ENCRYPTED_SUFFIX);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}