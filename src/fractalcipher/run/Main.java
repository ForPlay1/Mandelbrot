package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
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

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class Main {

    private static final String ENCRYPTED_SUFFIX = "_encrypted";
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "bmp", "gif");

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            String safe = ErrorHandler.handle(t);
            System.err.println(safe);
            System.exit(1);
        }
    }

    private static void run() {
        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();

        CipherController controller = new CipherController();
        ImageView view = new ConsoleView();

        String mode = view.requestMode();

        // === ПАКЕТНАЯ ОБРАБОТКА ПАПОК ===
        if ("bencrypt".equalsIgnoreCase(mode)) {
            runBatchEncrypt(view, controller, permutationFactory, diffusionFactory);
            return;
        }
        if ("bendecrypt".equalsIgnoreCase(mode)) {
            runBatchDecrypt(view, controller, permutationFactory, diffusionFactory);
            return;
        }

        // === ОДИНОЧНЫЕ ОПЕРАЦИИ ===
        // Внимание: для поиска ключа нам нужен именно объект File.
        System.out.print("Путь к изображению: ");
        Scanner scanner = new Scanner(System.in);
        File inputFile = new File(scanner.nextLine());

        Image image;
        try {
            image = Image.loadFromFile(inputFile.getName(), inputFile.getName(), inputFile);
        } catch (IOException e) {
            view.showError("Не удалось прочитать файл: " + e.getMessage());
            return;
        }

        if (image == null) return;

        if ("benchmark".equalsIgnoreCase(mode)) {
            runBenchmark(view, controller, permutationFactory, diffusionFactory, image);
            return;
        }

        runSingle(view, controller, permutationFactory, diffusionFactory, image, inputFile, mode);
    }

    // === ПАКЕТНОЕ ШИФРОВАНИЕ ПАПКИ ===
    private static void runBatchEncrypt(ImageView view, CipherController controller,
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

        System.out.println("Найдено файлов: " + files.length);
        int ok = 0;
        int failed = 0;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String prefix = "[" + (i + 1) + "/" + files.length + "] " + file.getName() + ": ";
            try {
                Image input = Image.loadFromFile(file.getName(), file.getName(), file);

                // 1. Генерируем уникальный сид
                long masterSeed = new SecureRandom().nextLong();

                // 2. Сохраняем ключ
                EncryptionKey key = new EncryptionKey(masterSeed, permutationName, diffusionName);
                File targetImage = new File(dir, baseName(file.getName()) + ENCRYPTED_SUFFIX + ".png");
                File targetKey = KeyManager.getKeyFile(targetImage);
                KeyManager.saveKey(key, targetKey);

                // 3. Восстанавливаем параметры
                Map<String, Double> permParams = buildPermParams(masterSeed, permutationName);
                Map<String, Double> diffParams = buildDiffParams(masterSeed, diffusionName);

                FractalChaosCipher cipher = new FractalChaosCipher(
                        "batch", permutationName + "+" + diffusionName,
                        permutationFactory.get(permutationName),
                        diffusionFactory.get(diffusionName),
                        permParams, diffParams
                );

                // 4. Шифруем и сохраняем
                Image result = controller.handleEncrypt(cipher, input);
                if (!ImageIO.write(result.getImage(), "png", targetImage)) {
                    throw new IOException("не удалось записать PNG");
                }

                System.out.println(prefix + "OK -> " + targetImage.getName() + " (+ ключ)");
                ok++;
            } catch (Throwable t) {
                view.showError(prefix + ErrorHandler.handle(t));
                failed++;
            }
        }

        System.out.println("Готово. Успешно: " + ok + ", с ошибкой: " + failed);
    }

    // === ПАКЕТНАЯ РАСШИФРОВКА ПАПКИ ===
    private static void runBatchDecrypt(ImageView view, CipherController controller,
                                        PermutationSourceFactory permutationFactory,
                                        DiffusionSourceFactory diffusionFactory) {
        File dir = view.requestFolder();
        if (dir == null) return;

        // Ищем только те PNG, у которых есть суффикс _encrypted
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(ENCRYPTED_SUFFIX + ".png"));
        if (files == null || files.length == 0) {
            view.showError("В папке нет зашифрованных изображений (*_encrypted.png)");
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

                // 4. Сохраняем (убираем _encrypted и добавляем _decrypted)
                String decName = baseName(file.getName()).replace(ENCRYPTED_SUFFIX, "") + "_decrypted.png";
                File targetImage = new File(dir, decName);
                if (!ImageIO.write(result.getImage(), "png", targetImage)) {
                    throw new IOException("не удалось записать PNG");
                }

                System.out.println(prefix + "OK -> " + targetImage.getName());
                ok++;
            } catch (Throwable t) {
                view.showError(prefix + ErrorHandler.handle(t));
                failed++;
            }
        }

        System.out.println("Готово. Успешно: " + ok + ", с ошибкой: " + failed);
    }

    // === ОДИНОЧНОЕ ШИФРОВАНИЕ / РАСШИФРОВКА ===
    private static void runSingle(ImageView view,
                                  CipherController controller,
                                  PermutationSourceFactory permutationFactory,
                                  DiffusionSourceFactory diffusionFactory,
                                  Image image,
                                  File inputFile,
                                  String mode) {

        boolean isDecrypt = "decrypt".equalsIgnoreCase(mode);

        File targetFile;
        File keyFile;
        long masterSeed;
        String permutationName;
        String diffusionName;

        if (isDecrypt) {
            targetFile = inputFile;
            keyFile = KeyManager.getKeyFile(targetFile);

            if (!keyFile.exists()) {
                throw new InvalidInputException("Файл ключа не найден! Ожидался: " + keyFile.getAbsolutePath());
            }

            System.out.println("Найден файл ключа: " + keyFile.getName());

            EncryptionKey key;
            try {
                key = KeyManager.loadKey(keyFile);
            } catch (IOException | ClassNotFoundException e) {
                throw new EncryptionFailedException("Не удалось прочитать ключ: " + e.getMessage(), e);
            }

            masterSeed = key.masterSeed;
            permutationName = key.permutationSourceName;
            diffusionName = key.diffusionSourceName;
            System.out.println("Загружены параметры: " + permutationName + " + " + diffusionName);

        } else {
            String originalName = inputFile.getName();
            int dot = originalName.lastIndexOf('.');
            String baseNameStr = (dot == -1) ? originalName : originalName.substring(0, dot);
            targetFile = new File(inputFile.getParentFile(), baseNameStr + ENCRYPTED_SUFFIX + ".png");
            keyFile = KeyManager.getKeyFile(targetFile);

            permutationName = view.requestPermutationName();
            diffusionName = view.requestDiffusionName();

            masterSeed = new SecureRandom().nextLong();

            EncryptionKey newKey = new EncryptionKey(masterSeed, permutationName, diffusionName);
            try {
                KeyManager.saveKey(newKey, keyFile);
                System.out.println("Ключ успешно сохранен в: " + keyFile.getAbsolutePath());
            } catch (IOException e) {
                view.showError("Не удалось сохранить ключ: " + e.getMessage());
                return;
            }
        }

        Map<String, Double> permParams = buildPermParams(masterSeed, permutationName);
        Map<String, Double> diffParams = buildDiffParams(masterSeed, diffusionName);

        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName),
                permParams,
                diffParams
        );

        Image result = isDecrypt
                ? controller.handleDecrypt(cipher, image)
                : controller.handleEncrypt(cipher, image);

        if (!isDecrypt) {
            try {
                if (!ImageIO.write(result.getImage(), "png", targetFile)) {
                    throw new IOException("Не удалось записать PNG файл");
                }
                System.out.println("Зашифрованное изображение сохранено в: " + targetFile.getAbsolutePath());
            } catch (IOException e) {
                throw new EncryptionFailedException("Ошибка при сохранении: " + e.getMessage(), e);
            }
        }

        view.showResult(result);
    }

    // === БЕНЧМАРК ===
    private static void runBenchmark(ImageView view, CipherController controller,
                                     PermutationSourceFactory permutationFactory,
                                     DiffusionSourceFactory diffusionFactory, Image image) {
        System.out.println("\n=== СЕРИЯ 1: ТОЛЬКО ПЕРЕСТАНОВКА ===");
        List<BenchmarkResult> permOnly = controller.handleBenchmarkPermutationOnly(permutationFactory, image);
        view.showBenchmarkResults(permOnly);

        System.out.println("\n=== СЕРИЯ 2: ТОЛЬКО ДИФФУЗИЯ ===");
        List<BenchmarkResult> diffOnly = controller.handleBenchmarkDiffusionOnly(diffusionFactory, image);
        view.showBenchmarkResults(diffOnly);

        System.out.println("\n=== СЕРИЯ 3: ПОЛНАЯ СХЕМА ===");
        List<BenchmarkResult> full = controller.handleBenchmarkFull(permutationFactory, diffusionFactory, image);
        view.showBenchmarkResults(full);
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