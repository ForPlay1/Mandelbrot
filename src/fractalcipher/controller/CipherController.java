package fractalcipher.controller;

import fractalcipher.domain.*;
import fractalcipher.exception.ErrorHandler;
import fractalcipher.exception.InvalidInputException;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.metrics.CsvExporter;
import fractalcipher.metrics.BenchmarkRunner;
import fractalcipher.model.EncryptionKey;
import fractalcipher.model.KeyManager;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.utils.CipherUtils;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;

public class CipherController {

    private final PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
    private final DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();
    private final ImageView view = new ConsoleView();

    /**
     * Главная точка входа для бизнес-логики.
     * Вызывается из Main после того, как пользователь нажал "Запустить".
     */
    public void processSelection(Map<Class<?>, Object> selection) {
        CipherMode mode = (CipherMode) selection.get(CipherMode.class);
        PermutationMode perm = (PermutationMode) selection.get(PermutationMode.class);
        DiffusionMode diff = (DiffusionMode) selection.get(DiffusionMode.class);
        File input = (File) selection.get(File.class);
        Path out = (Path) selection.get(Path.class);

        File output = (out != null) ? out.toFile() : null;

        if (mode == null || input == null) {
            System.out.println("Режим или входной файл не выбраны — выходим.");
            return;
        }

        try {
            switch (mode) {
                case BENCRYPT -> runBatchEncrypt(perm.getName(), diff.getName(), input, output);
                case BENDECRYPT -> runBatchDecrypt(input, output);
                case BENCHMARK -> runBenchmark(input);
                case ENCRYPT, DECRYPT -> {
                    Image image = Image.loadFromFile(input.getName(), input.getName(), input);
                    runSingle(mode, (perm != null ? perm.getName() : null), (diff != null ? diff.getName() : null), input, image);
                }
            }
        } catch (Throwable t) {
            view.showError(ErrorHandler.handle(t));
        }
    }

    // === ОДИНОЧНОЕ ШИФРОВАНИЕ / РАСШИФРОВКА ===
    private void runSingle(CipherMode mode, String permutationName, String diffusionName,
                           File inputFile, Image image) {
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
            String baseNameStr = CipherUtils.baseName(originalName);
            targetFile = new File(inputFile.getParentFile(), baseNameStr + CipherUtils.ENCRYPTED_SUFFIX + ".png");
            keyFile = KeyManager.getKeyFile(targetFile);

            masterSeed = new SecureRandom().nextLong();
            EncryptionKey newKey = new EncryptionKey(masterSeed, permutationName, diffusionName);
            try {
                KeyManager.saveKey(newKey, keyFile);
            } catch (IOException e) {
                throw new RuntimeException("Не удалось сохранить ключ: " + e.getMessage());
            }
        }

        Map<String, Double> permParams = CipherUtils.buildPermParams(masterSeed, permutationName);
        Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterSeed, diffusionName);

        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName),
                permParams, diffParams
        );

        Image result = isDecrypt
                ? handleDecrypt(cipher, image)
                : handleEncrypt(cipher, image);

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
    private void runBatchEncrypt(String permutationName, String diffusionName,
                                 File inputDir, File outputDir) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && CipherUtils.isImageToEncrypt(f.getName()));
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
                long masterSeed = new SecureRandom().nextLong();

                EncryptionKey key = new EncryptionKey(masterSeed, permutationName, diffusionName);
                File targetImage = new File(outputDir, CipherUtils.baseName(file.getName()) + CipherUtils.ENCRYPTED_SUFFIX + ".png");
                File targetKey = KeyManager.getKeyFile(targetImage);
                KeyManager.saveKey(key, targetKey);

                Map<String, Double> permParams = CipherUtils.buildPermParams(masterSeed, permutationName);
                Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterSeed, diffusionName);

                FractalChaosCipher cipher = new FractalChaosCipher(
                        "batch", permutationName + "+" + diffusionName,
                        permutationFactory.get(permutationName),
                        diffusionFactory.get(diffusionName),
                        permParams, diffParams
                );

                Image result = handleEncrypt(cipher, input);
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
    private void runBatchDecrypt(File inputDir, File outputDir) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && f.getName().endsWith(CipherUtils.ENCRYPTED_SUFFIX + ".png"));
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
                File keyFile = KeyManager.getKeyFile(file);
                if (!keyFile.exists()) {
                    throw new InvalidInputException("Ключ не найден: " + keyFile.getName());
                }
                EncryptionKey key = KeyManager.loadKey(keyFile);

                Map<String, Double> permParams = CipherUtils.buildPermParams(key.masterSeed, key.permutationSourceName);
                Map<String, Double> diffParams = CipherUtils.buildDiffParams(key.masterSeed, key.diffusionSourceName);

                FractalChaosCipher cipher = new FractalChaosCipher(
                        "batch", key.permutationSourceName + "+" + key.diffusionSourceName,
                        permutationFactory.get(key.permutationSourceName),
                        diffusionFactory.get(key.diffusionSourceName),
                        permParams, diffParams
                );

                Image input = Image.loadFromFile(file.getName(), file.getName(), file);
                Image result = handleDecrypt(cipher, input);

                String decName = CipherUtils.baseName(file.getName()).replace(CipherUtils.ENCRYPTED_SUFFIX, "") + "_decrypted.png";
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

    // === БЕНЧМАРК ===
    private void runBenchmark(File inputFile) {
        List<File> imagesToProcess = new ArrayList<>();

        if (inputFile.isDirectory()) {
            File[] files = inputFile.listFiles(f -> f.isFile() && CipherUtils.isImageToEncrypt(f.getName()));
            if (files != null) {
                imagesToProcess.addAll(Arrays.asList(files));
            }
        } else if (inputFile.isFile()) {
            imagesToProcess.add(inputFile);
        }

        if (imagesToProcess.isEmpty()) {
            view.showError("Не найдено изображений для бенчмарка.");
            return;
        }

        System.out.println("\n=== БЕНЧМАРК: Найдено изображений: " + imagesToProcess.size() + " ===");
        List<BenchmarkResult> allResults = Collections.synchronizedList(new ArrayList<>());

        // УБРАЛИ parallelStream ЗДЕСЬ. Теперь это обычный цикл.
        for (File file : imagesToProcess) {
            System.out.println("Анализ изображения: " + file.getName());
            try {
                Image image = Image.loadFromFile(file.getName(), file.getName(), file);
                List<BenchmarkResult> localResults = new ArrayList<>();
                localResults.addAll(handleBenchmarkPermutationOnly(image));
                localResults.addAll(handleBenchmarkDiffusionOnly(image));
                localResults.addAll(handleBenchmarkFull(image));
                allResults.addAll(localResults);
            } catch (Throwable t) {
                System.err.println("Ошибка при бенчмарке файла " + file.getName() + ": " + t.getMessage());
            }
        }

        try {
            String csvBaseName = inputFile.isDirectory() ? inputFile.getName() : "single_image";
            CsvExporter.export(allResults, "benchmark_" + csvBaseName);
        } catch (IOException e) {
            view.showError("Не удалось сохранить CSV: " + e.getMessage());
        }

        view.showBenchmarkResults(allResults);
    }

    // --- Старые методы делегирования (без изменений) ---
    public Image handleEncrypt(Cipher cipher, Image input) {
        cipher.setImageInput(input);
        cipher.encrypt();
        return cipher.getImageOutput();
    }

    public Image handleDecrypt(Cipher cipher, Image input) {
        cipher.setImageInput(input);
        cipher.decrypt();
        return cipher.getImageOutput();
    }

    public List<BenchmarkResult> handleBenchmarkPermutationOnly(Image image) {
        return BenchmarkRunner.runPermutationOnly(permutationFactory, image);
    }

    public List<BenchmarkResult> handleBenchmarkDiffusionOnly(Image image) {
        return BenchmarkRunner.runDiffusionOnly(diffusionFactory, image);
    }

    public List<BenchmarkResult> handleBenchmarkFull(Image image) {
        return BenchmarkRunner.runFull(permutationFactory, diffusionFactory, image);
    }
}