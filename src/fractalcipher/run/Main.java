package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.domain.CipherMode;
import fractalcipher.domain.DiffusionMode;
import fractalcipher.domain.PermutationMode;
import fractalcipher.exception.EncryptionFailedException;
import fractalcipher.exception.ErrorHandler;
import fractalcipher.exception.InvalidInputException;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.model.EncryptionKey;
import fractalcipher.model.KeyDerivation;
import fractalcipher.model.KeyManager;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;
import fractalcipher.view.MainView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class Main extends Application
{

    private static final String ENCRYPTED_SUFFIX = "_encrypted";
    private static final List<String> IMAGE_EXTENSIONS = Image.getImageExtensions();

    @Override
    public void start(Stage stage)
    {
        MainView view = new MainView();
        Scene scene = new Scene(view.getRoot(), 1300, 750);
        stage.setTitle("Фрактальное шифрование");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args)
    {
        // === ЦЕНТРАЛИЗОВАННАЯ ОБРАБОТКА ОШИБОК ===
        // Единственное место с глобальным try-catch.
        // Все исключения из run() попадают сюда и обрабатываются
        // через ErrorHandler — единообразно, безопасно, с логированием.
        try {
            // Интерфейс блокирует основной поток, поэтому
            // запускаем дополнительный поток, который ждет
            // закрытия интерфейса и вытаскивает из него данные
            Thread worker = new Thread(() ->
            {
                Map<Class<?>, Object> selection;
                do
                {
                    selection = MainView.getUserParams();
                    if (selection == null)
                    {
                        try { Thread.sleep(100); }
                        catch (InterruptedException ignored) { return; }
                    }
                } while (selection == null);

                run(selection);
            });
            worker.setDaemon(true);
            worker.start();

            launch(args);
        } catch (Throwable t)
        {
            String safe = ErrorHandler.handle(t);
            System.err.println(safe);
            System.exit(1);
        }
    }

    /**
     * Вся бизнес-логика приложения. Исключения НЕ обрабатываются здесь —
     * они пробрасываются наверх, в глобальный перехватчик.
     */
    private static void run(Map<Class<?>, Object> selection)
    {
        CipherMode      mode = (CipherMode)      selection.get(CipherMode.class);
        PermutationMode perm = (PermutationMode) selection.get(PermutationMode.class);
        DiffusionMode   diff = (DiffusionMode)   selection.get(DiffusionMode.class);
        File            input    = (File)            selection.get(File.class);
        Path            out   = (Path)            selection.get(Path.class);
        File            output = out.toFile();



        if (mode == null || perm == null || diff == null
                || input == null || output == null)
        {
            System.out.println("Не все параметры выбраны — выходим.");
            return;
        }


        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();

        CipherController controller = new CipherController();
        ImageView view = new ConsoleView();

        switch(mode){
            // Пакетное шифрование папки
            case CipherMode.BENCRYPT ->
            {
                runBatchEncrypt(
                        perm.getName(), diff.getName(), input, output,
                        controller, permutationFactory, diffusionFactory, view
                );
                return;
            }
            // Бенчмарк: 3 серии
            case CipherMode.BENCHMARK ->
            {
                Image image;
                try {
                    image = Image.loadFromFile(input.getName(), input.getName(), input);
                } catch (Throwable t) {
                    String safe = ErrorHandler.handle(t);
                    view.showError(safe);
                    return;
                }

                if (image == null) return;

                runBenchmark(image, controller, permutationFactory, diffusionFactory, view);
                return;
            }
            case CipherMode.ENCRYPT, CipherMode.DECRYPT ->
            {
                Image image;
                try {
                    image = Image.loadFromFile(input.getName(), input.getName(), input);
                } catch (Exception e) {
                    System.out.println("Не удалось загрузить изображение: " + e.getMessage());
                    return;
                }

                if (image == null) return;

                runSingle(mode.getName(), perm.getName(), diff.getName(), image, controller, permutationFactory, diffusionFactory, view);
                return;
            }
            default -> {}
        }
    }

    // === БЕНЧМАРК: три серии ===
    private static void runBenchmark(Image image,
                                     CipherController controller,
                                     PermutationSourceFactory permutationFactory,
                                     DiffusionSourceFactory diffusionFactory,
                                     ImageView view
                                     ) {

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
    private static void runSingle(String mode,
                                  String permutationName,
                                  String diffusionName,
                                  Image image,
                                  CipherController controller,
                                  PermutationSourceFactory permutationFactory,
                                  DiffusionSourceFactory diffusionFactory,
                                  ImageView view
                                  ) {


        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName)
        );

        Image result = (mode.equals(CipherMode.DECRYPT.getName()))
                ? controller.handleDecrypt(cipher, image)
                : controller.handleEncrypt(cipher, image);
        view.showResult(result);
    }

    // Шифрует все изображения из папки выбранным шифром. Результаты кладутся
    // в ту же папку: <имя>_encrypted.png. Всегда PNG — он без потерь; JPEG
    // испортил бы зашифрованные пиксели и расшифровка стала бы невозможной.
    private static void runBatchEncrypt(String permutationName,
                                        String diffusionName,
                                        File inputDir,
                                        File outputDir,
                                        CipherController controller,
                                        PermutationSourceFactory permutationFactory,
                                        DiffusionSourceFactory diffusionFactory,
                                        ImageView view) {
        if (inputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && isImageToEncrypt(f.getName()));
        if (files == null || files.length == 0) {
            view.showError("В папке нет изображений для шифрования (png, jpg, jpeg, bmp, gif)");
            return;
        }
        Arrays.sort(files);

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
        Arrays.sort(files);

        System.out.println("Найдено файлов для расшифровки: " + files.length);
        int ok = 0;
        int failed = 0;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String prefix = "[" + (i + 1) + "/" + files.length + "] " + file.getName() + ": ";
            try {
                // 1. Ищем и загружаем ключ
                File keyFile = KeyManager.getKeyFile(file);
                if (!keyFile.exists()) {
                    throw new InvalidInputException("Ключ не найден: " + keyFile.getName());
                }
                EncryptionKey key = KeyManager.loadKey(keyFile);

                // 2. Восстанавливаем параметры
                Map<String, Double> permParams = buildPermParams(key.masterSeed, key.permutationSourceName);
                Map<String, Double> diffParams = buildDiffParams(key.masterSeed, key.diffusionSourceName);

                FractalChaosCipher cipher = new FractalChaosCipher(
                        "batch", key.permutationSourceName + "+" + key.diffusionSourceName,
                        permutationFactory.get(key.permutationSourceName),
                        diffusionFactory.get(key.diffusionSourceName),
                        permParams, diffParams
                );

                // 3. Расшифровываем
                Image input = Image.loadFromFile(file.getName(), file.getName(), file);
                Image result = controller.handleDecrypt(cipher, input);

                File target = new File(outputDir, baseName(file.getName()) + ENCRYPTED_SUFFIX + ".png");
                if (!ImageIO.write(result.getImage(), "png", target)) {
                // 4. Сохраняем (убираем _encrypted и добавляем _decrypted)
                String decName = baseName(file.getName()).replace(ENCRYPTED_SUFFIX, "") + "_decrypted.png";
                File targetImage = new File(dir, decName);
                if (!ImageIO.write(result.getImage(), "png", targetImage)) {
                    throw new IOException("не удалось записать PNG");
                }

                System.out.println(prefix + "OK -> " + targetImage.getName());
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