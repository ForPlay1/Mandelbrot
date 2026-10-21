package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.domain.CipherMode;
import fractalcipher.domain.DiffusionMode;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.domain.PermutationMode;
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

public class Main extends Application {

    private static final String ENCRYPTED_SUFFIX = "_encrypted";
    private static final List<String> IMAGE_EXTENSIONS = Image.getImageExtensions();

    @Override
    public void start(Stage stage) {
        MainView view = new MainView();
        Scene scene = new Scene(view.getRoot(), 1300, 750);
        stage.setTitle("Фрактальное шифрование");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        try {
            Thread worker = new Thread(() -> {
                Map<Class<?>, Object> selection;
                do {
                    selection = MainView.getUserParams();
                    if (selection == null) {
                        try { Thread.sleep(100); }
                        catch (InterruptedException ignored) { return; }
                    }
                } while (selection == null);

                run(selection);
            });
            worker.setDaemon(true);
            worker.start();

            launch(args);
        } catch (Throwable t) {
            String safe = ErrorHandler.handle(t);
            System.err.println(safe);
            System.exit(1);
        }
    }

    private static void run(Map<Class<?>, Object> selection) {
        CipherMode      mode = (CipherMode)      selection.get(CipherMode.class);
        PermutationMode perm = (PermutationMode) selection.get(PermutationMode.class);
        DiffusionMode   diff = (DiffusionMode)   selection.get(DiffusionMode.class);
        File            input = (File)           selection.get(File.class);
        Path            out   = (Path)           selection.get(Path.class);

        File output = (out != null) ? out.toFile() : null;

        // Проверяем только самое необходимое (режим и входной файл)
        if (mode == null || input == null) {
            System.out.println("Режим или входной файл не выбраны — выходим.");
            return;
        }

        // Методы (перестановка и диффузия) обязательны ТОЛЬКО для шифрования
        boolean needMethods = (mode == CipherMode.ENCRYPT || mode == CipherMode.BENCRYPT || mode == CipherMode.BENCHMARK);
        if (needMethods && (perm == null || diff == null)) {
            System.out.println("Для шифрования необходимо выбрать методы — выходим.");
            return;
        }

        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();
        CipherController controller = new CipherController();
        ImageView view = new ConsoleView();

        switch (mode) {
            case BENCRYPT -> {
                runBatchEncrypt(perm.getName(), diff.getName(), input, output,
                        controller, permutationFactory, diffusionFactory, view);
            }
            case BENDECRYPT -> {
                runBatchDecrypt(input, output, controller, permutationFactory, diffusionFactory, view);
            }
            case BENCHMARK -> {
                try {
                    Image image = Image.loadFromFile(input.getName(), input.getName(), input);
                    runBenchmark(image, controller, permutationFactory, diffusionFactory, view);
                } catch (Throwable t) {
                    view.showError(ErrorHandler.handle(t));
                }
            }
            case ENCRYPT, DECRYPT -> {
                try {
                    Image image = Image.loadFromFile(input.getName(), input.getName(), input);
                    // Если это DECRYPT, perm и diff могут быть null - это нормально, runSingle возьмет их из ключа
                    runSingle(mode, (perm != null ? perm.getName() : null), (diff != null ? diff.getName() : null), input, image, controller, permutationFactory, diffusionFactory, view);
                } catch (Throwable t) {
                    view.showError(ErrorHandler.handle(t));
                }
            }
        }
    }

    // === БЕНЧМАРК ===
    private static void runBenchmark(Image image, CipherController controller,
                                     PermutationSourceFactory permutationFactory,
                                     DiffusionSourceFactory diffusionFactory,
                                     ImageView view) {
        System.out.println("\n=== СЕРИЯ 1: ТОЛЬКО ПЕРЕСТАНОВКА ===");
        view.showBenchmarkResults(controller.handleBenchmarkPermutationOnly(permutationFactory, image));

        System.out.println("\n=== СЕРИЯ 2: ТОЛЬКО ДИФФУЗИЯ ===");
        view.showBenchmarkResults(controller.handleBenchmarkDiffusionOnly(diffusionFactory, image));

        System.out.println("\n=== СЕРИЯ 3: ПОЛНАЯ СХЕМА ===");
        view.showBenchmarkResults(controller.handleBenchmarkFull(permutationFactory, diffusionFactory, image));
    }

    // === ОДИНОЧНОЕ ШИФРОВАНИЕ / РАСШИФРОВКА ===
    private static void runSingle(CipherMode mode, String permutationName, String diffusionName,
                                  File inputFile, Image image, CipherController controller,
                                  PermutationSourceFactory permutationFactory,
                                  DiffusionSourceFactory diffusionFactory,
                                  ImageView view) {

        boolean isDecrypt = (mode == CipherMode.DECRYPT);
        File targetFile;
        File keyFile;
        long masterSeed;

        if (isDecrypt) {
            targetFile = inputFile;
            keyFile = KeyManager.getKeyFile(targetFile);
            if (!keyFile.exists()) {
                throw new InvalidInputException("Файл ключа не найден: " + keyFile.getAbsolutePath());
            }
            try {
                EncryptionKey key = KeyManager.loadKey(keyFile);
                masterSeed = key.masterSeed;
                permutationName = key.permutationSourceName;
                diffusionName = key.diffusionSourceName;
            } catch (Exception e) {
                throw new RuntimeException("Ошибка чтения ключа: " + e.getMessage());
            }
        } else {
            String originalName = inputFile.getName();
            int dot = originalName.lastIndexOf('.');
            String baseNameStr = (dot == -1) ? originalName : originalName.substring(0, dot);
            targetFile = new File(inputFile.getParentFile(), baseNameStr + ENCRYPTED_SUFFIX + ".png");
            keyFile = KeyManager.getKeyFile(targetFile);

            masterSeed = new SecureRandom().nextLong();
            EncryptionKey newKey = new EncryptionKey(masterSeed, permutationName, diffusionName);
            try {
                KeyManager.saveKey(newKey, keyFile);
            } catch (IOException e) {
                throw new RuntimeException("Не удалось сохранить ключ: " + e.getMessage());
            }
        }

        Map<String, Double> permParams = buildPermParams(masterSeed, permutationName);
        Map<String, Double> diffParams = buildDiffParams(masterSeed, diffusionName);

        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName),
                permParams, diffParams
        );

        Image result = isDecrypt
                ? controller.handleDecrypt(cipher, image)
                : controller.handleEncrypt(cipher, image);

        if (!isDecrypt) {
            try {
                ImageIO.write(result.getImage(), "png", targetFile);
                System.out.println("Зашифровано в: " + targetFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Ошибка сохранения: " + e.getMessage());
            }
        }
        view.showResult(result);
    }

    // === ПАКЕТНОЕ ШИФРОВАНИЕ ПАПКИ ===
    private static void runBatchEncrypt(String permutationName, String diffusionName,
                                        File inputDir, File outputDir,
                                        CipherController controller,
                                        PermutationSourceFactory permutationFactory,
                                        DiffusionSourceFactory diffusionFactory,
                                        ImageView view) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && isImageToEncrypt(f.getName()));
        if (files == null || files.length == 0) {
            view.showError("В папке нет изображений для шифрования.");
            return;
        }
        Arrays.sort(files);

        System.out.println("Найдено файлов для шифрования: " + files.length);
        int ok = 0, failed = 0;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String prefix = "[" + (i + 1) + "/" + files.length + "] " + file.getName() + ": ";
            try {
                Image input = Image.loadFromFile(file.getName(), file.getName(), file);

                // 1. Сид и ключ
                long masterSeed = new SecureRandom().nextLong();
                EncryptionKey key = new EncryptionKey(masterSeed, permutationName, diffusionName);
                File targetImage = new File(outputDir, baseName(file.getName()) + ENCRYPTED_SUFFIX + ".png");
                File targetKey = KeyManager.getKeyFile(targetImage);
                KeyManager.saveKey(key, targetKey);

                // 2. Параметры
                Map<String, Double> permParams = buildPermParams(masterSeed, permutationName);
                Map<String, Double> diffParams = buildDiffParams(masterSeed, diffusionName);

                // 3. Шифрование
                FractalChaosCipher cipher = new FractalChaosCipher(
                        "batch", permutationName + "+" + diffusionName,
                        permutationFactory.get(permutationName),
                        diffusionFactory.get(diffusionName),
                        permParams, diffParams
                );

                Image result = controller.handleEncrypt(cipher, input);
                ImageIO.write(result.getImage(), "png", targetImage);

                System.out.println(prefix + "OK -> " + targetImage.getName());
                ok++;
            } catch (Throwable t) {
                view.showError(prefix + ErrorHandler.handle(t));
                failed++;
            }
        }
        System.out.println("Готово. Успешно: " + ok + ", с ошибкой: " + failed);
    }

    // === ПАКЕТНАЯ РАСШИФРОВКА ПАПКИ ===
    private static void runBatchDecrypt(File inputDir, File outputDir,
                                        CipherController controller,
                                        PermutationSourceFactory permutationFactory,
                                        DiffusionSourceFactory diffusionFactory,
                                        ImageView view) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && f.getName().endsWith(ENCRYPTED_SUFFIX + ".png"));
        if (files == null || files.length == 0) {
            view.showError("В папке нет зашифрованных изображений (*_encrypted.png).");
            return;
        }
        Arrays.sort(files);

        System.out.println("Найдено файлов для расшифровки: " + files.length);
        int ok = 0, failed = 0;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String prefix = "[" + (i + 1) + "/" + files.length + "] " + file.getName() + ": ";
            try {
                // 1. Ключ
                File keyFile = KeyManager.getKeyFile(file);
                if (!keyFile.exists()) {
                    throw new InvalidInputException("Ключ не найден: " + keyFile.getName());
                }
                EncryptionKey key = KeyManager.loadKey(keyFile);

                // 2. Параметры
                Map<String, Double> permParams = buildPermParams(key.masterSeed, key.permutationSourceName);
                Map<String, Double> diffParams = buildDiffParams(key.masterSeed, key.diffusionSourceName);

                // 3. Расшифровка
                FractalChaosCipher cipher = new FractalChaosCipher(
                        "batch", key.permutationSourceName + "+" + key.diffusionSourceName,
                        permutationFactory.get(key.permutationSourceName),
                        diffusionFactory.get(key.diffusionSourceName),
                        permParams, diffParams
                );

                Image input = Image.loadFromFile(file.getName(), file.getName(), file);
                Image result = controller.handleDecrypt(cipher, input);

                String decName = baseName(file.getName()).replace(ENCRYPTED_SUFFIX, "") + "_decrypted.png";
                File targetImage = new File(outputDir, decName);
                ImageIO.write(result.getImage(), "png", targetImage);

                System.out.println(prefix + "OK -> " + targetImage.getName());
                ok++;
            } catch (Throwable t) {
                view.showError(prefix + ErrorHandler.handle(t));
                failed++;
            }
        }
        System.out.println("Готово. Успешно: " + ok + ", с ошибкой: " + failed);
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===
    private static Map<String, Double> buildPermParams(long masterSeed, String permName) {
        Map<String, Double> map = new HashMap<>();
        map.put("seed", (double) KeyDerivation.deriveLong(masterSeed, "perm:" + permName));
        return map;
    }

    private static Map<String, Double> buildDiffParams(long masterSeed, String diffName) {
        Map<String, Double> map = new HashMap<>();
        map.put("seed", (double) KeyDerivation.deriveLong(masterSeed, "diff:" + diffName));
        map.put("x0", KeyDerivation.deriveDouble(masterSeed, "x0:" + diffName));
        map.put("sboxSeed", (double) KeyDerivation.deriveLong(masterSeed, "sbox:" + diffName));
        map.put("sboxCbc1", (double) KeyDerivation.deriveLong(masterSeed, "cbc1:" + diffName));
        map.put("sboxCbc2", (double) KeyDerivation.deriveLong(masterSeed, "cbc2:" + diffName));
        return map;
    }

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