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
import fractalcipher.utils.AppLogger;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
            AppLogger.warning("Запуск отменен: не выбран режим или входной файл.");
            System.out.println("Режим или входной файл не выбраны — выходим.");
            return;
        }

        // Логируем старт операции
        AppLogger.info("--- Запуск операции ---");
        AppLogger.info("Режим: " + mode.name() + ", Вход: " + input.getAbsolutePath());
        if (output != null) {
            AppLogger.info("Выходная папка: " + output.getAbsolutePath());
        }

        try {
            switch (mode) {
                case BENCRYPT -> runBatchEncrypt(perm.getName(), diff.getName(), input, output);
                case BENDECRYPT -> runBatchDecrypt(input, output);
                case BENCHMARK -> runBenchmark(input);
                case ENCRYPT, DECRYPT -> {
                    Image image = Image.loadFromFile(input.getName(), input.getName(), input);
                    // Передаем output в runSingle
                    runSingle(mode, (perm != null ? perm.getName() : null), (diff != null ? diff.getName() : null), input, image, output);
                }
            }
            AppLogger.info("--- Операция успешно завершена ---");
        } catch (Throwable t) {
            // ErrorHandler.handle() внутри себя вызовет AppLogger.error(), если логгер инициализирован
            String safeMsg = ErrorHandler.handle(t);
            AppLogger.error("--- Операция аварийно завершена ---", t);
            view.showError(safeMsg);
        }
    }

    // === ОДИНОЧНОЕ ШИФРОВАНИЕ / РАСШИФРОВКА ===
    private void runSingle(CipherMode mode, String permutationName, String diffusionName,
                           File inputFile, Image image, File outputDir) {
        boolean isDecrypt = (mode == CipherMode.DECRYPT);
        String operation = isDecrypt ? "Расшифровка" : "Шифрование";
        AppLogger.info(operation + " файла: " + inputFile.getName() + " [" + permutationName + " + " + diffusionName + "]");

        File targetFile;
        File keyFile;
        long masterSeed;

        // Определяем папку для сохранения (если не выбрана, берем папку исходника)
        File saveDir = (outputDir != null) ? outputDir : inputFile.getParentFile();

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
                AppLogger.info("Ключ успешно загружен из: " + keyFile.getName());
            } catch (Exception e) {
                AppLogger.error("Ошибка чтения ключа: " + keyFile.getName(), e);
                throw new RuntimeException("Ошибка чтения ключа: " + e.getMessage());
            }
        } else {
            String originalName = inputFile.getName();
            String baseNameStr = CipherUtils.baseName(originalName);

            // Сохраняем зашифрованный файл в выбранную папку saveDir
            targetFile = new File(saveDir, baseNameStr + CipherUtils.ENCRYPTED_SUFFIX + ".png");
            keyFile = KeyManager.getKeyFile(targetFile);

            masterSeed = new SecureRandom().nextLong();
            EncryptionKey newKey = new EncryptionKey(masterSeed, permutationName, diffusionName);
            try {
                KeyManager.saveKey(newKey, keyFile);
                AppLogger.info("Сгенерирован и сохранен новый ключ: " + keyFile.getName());
            } catch (IOException e) {
                AppLogger.error("Не удалось сохранить ключ: " + keyFile.getName(), e);
                throw new RuntimeException("Не удалось сохранить ключ: " + e.getMessage());
            }
        }

        Map<String, Double> permParams = CipherUtils.buildPermParams(masterSeed, permutationName);
        Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterSeed, diffusionName);

        // КОНТЕКСТНЫЙ ПАРАЛЛЕЛИЗМ: для одного файла ВКЛЮЧАЕМ внутренний параллелизм
        permParams.put("isParallel", 1.0);
        diffParams.put("isParallel", 1.0);

        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName),
                permParams, diffParams
        );

        Image result = isDecrypt
                ? handleDecrypt(cipher, image)
                : handleEncrypt(cipher, image);

        // СОХРАНЕНИЕ РЕЗУЛЬТАТА
        try {
            if (isDecrypt) {
                // Формируем имя для расшифрованного файла
                String decName = CipherUtils.baseName(inputFile.getName()).replace(CipherUtils.ENCRYPTED_SUFFIX, "") + "_decrypted.png";
                File decFile = new File(saveDir, decName);
                ImageIO.write(result.getImage(), "png", decFile);
                AppLogger.info("Расшифровано: " + decFile.getName() + " (" + permutationName + " + " + diffusionName + ")");
            } else {
                // Сохраняем зашифрованный файл
                ImageIO.write(result.getImage(), "png", targetFile);
                AppLogger.info("Зашифровано: " + targetFile.getName() + " (" + permutationName + " + " + diffusionName + ")");
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сохранения: " + e.getMessage());
        }

        view.showResult(result);
    }

    // === ПАКЕТНОЕ ШИФРОВАНИЕ ПАПКИ ===
    private void runBatchEncrypt(String permutationName, String diffusionName,
                                 File inputDir, File outputDir) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && CipherUtils.isImageToEncrypt(f.getName()));
        if (files == null || files.length == 0) {
            AppLogger.warning("Пакетное шифрование отменено: в папке нет изображений.");
            view.showError("В папке нет изображений для шифрования.");
            return;
        }
        Arrays.sort(files);

        AppLogger.info("Начало пакетного шифрования. Файлов: " + files.length + " [" + permutationName + " + " + diffusionName + "]");
        long startTime = System.currentTimeMillis();

        AtomicInteger ok = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // КОНТЕКСТНЫЙ ПАРАЛЛЕЛИЗМ: Параллелим обработку САМИХ ФАЙЛОВ
        Arrays.stream(files).parallel().forEach(file -> {
            String prefix = file.getName() + ": ";
            try {
                Image input = Image.loadFromFile(file.getName(), file.getName(), file);
                long masterSeed = new SecureRandom().nextLong();

                EncryptionKey key = new EncryptionKey(masterSeed, permutationName, diffusionName);
                File targetImage = new File(outputDir, CipherUtils.baseName(file.getName()) + CipherUtils.ENCRYPTED_SUFFIX + ".png");
                File targetKey = KeyManager.getKeyFile(targetImage);
                KeyManager.saveKey(key, targetKey);

                Map<String, Double> permParams = CipherUtils.buildPermParams(masterSeed, permutationName);
                Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterSeed, diffusionName);

                // ВЫКЛЮЧАЕМ внутренний параллелизм для фракталов, чтобы не конфликтовать с параллелизмом файлов
                permParams.put("isParallel", 0.0);
                diffParams.put("isParallel", 0.0);

                FractalChaosCipher cipher = new FractalChaosCipher(
                        "batch", permutationName + "+" + diffusionName,
                        permutationFactory.get(permutationName),
                        diffusionFactory.get(diffusionName),
                        permParams, diffParams
                );

                Image result = handleEncrypt(cipher, input);
                ImageIO.write(result.getImage(), "png", targetImage);

                AppLogger.info("Пакетное шифрование [OK]: " + targetImage.getName());
                ok.incrementAndGet();
            } catch (Throwable t) {
                AppLogger.error("Пакетное шифрование [FAIL]: " + file.getName(), t);
                failed.incrementAndGet();
            }
        });
        long duration = System.currentTimeMillis() - startTime;
        AppLogger.info("Пакетное шифрование завершено за " + duration + " мс. Успешно: " + ok.get() + ", Ошибок: " + failed.get());
    }

    // === ПАКЕТНАЯ РАСШИФРОВКА ПАПКИ ===
    private void runBatchDecrypt(File inputDir, File outputDir) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && f.getName().endsWith(CipherUtils.ENCRYPTED_SUFFIX + ".png"));
        if (files == null || files.length == 0) {
            AppLogger.warning("Пакетная расшифровка отменена: в папке нет зашифрованных изображений.");
            view.showError("В папке нет зашифрованных изображений (*_encrypted.png).");
            return;
        }
        Arrays.sort(files);

        AppLogger.info("Начало пакетной расшифровки. Файлов: " + files.length);
        long startTime = System.currentTimeMillis();


        AtomicInteger ok = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // КОНТЕКСТНЫЙ ПАРАЛЛЕЛИЗМ: Параллелим обработку САМИХ ФАЙЛОВ
        Arrays.stream(files).parallel().forEach(file -> {
            String prefix = file.getName() + ": ";
            try {
                File keyFile = KeyManager.getKeyFile(file);
                if (!keyFile.exists()) {
                    throw new InvalidInputException("Ключ не найден: " + keyFile.getName());
                }
                EncryptionKey key = KeyManager.loadKey(keyFile);

                Map<String, Double> permParams = CipherUtils.buildPermParams(key.masterSeed, key.permutationSourceName);
                Map<String, Double> diffParams = CipherUtils.buildDiffParams(key.masterSeed, key.diffusionSourceName);

                // ВЫКЛЮЧАЕМ внутренний параллелизм для фракталов
                permParams.put("isParallel", 0.0);
                diffParams.put("isParallel", 0.0);

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

                AppLogger.info("Пакетная расшифровка [OK]: " + targetImage.getName());
                ok.incrementAndGet();
            } catch (Throwable t) {
                AppLogger.error("Пакетная расшифровка [FAIL]: " + file.getName(), t);
                failed.incrementAndGet();
            }
        });
        long duration = System.currentTimeMillis() - startTime;
        AppLogger.info("Пакетная расшифровка завершена за " + duration + " мс. Успешно: " + ok.get() + ", Ошибок: " + failed.get());
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
            AppLogger.warning("Бенчмарк отменен: не найдено изображений.");
            view.showError("Не найдено изображений для бенчмарка.");
            return;
        }

        System.out.println("\n=== БЕНЧМАРК: Найдено изображений: " + imagesToProcess.size() + " ===");
        AppLogger.info("Запуск бенчмарка. Изображений: " + imagesToProcess.size());
        List<BenchmarkResult> allResults = Collections.synchronizedList(new ArrayList<>());

        // Здесь последовательно по файлам, а параллелизм уже внутри BenchmarkRunner
        for (File file : imagesToProcess) {
            System.out.println("Анализ изображения: " + file.getName());
            try {
                Image image = Image.loadFromFile(file.getName(), file.getName(), file);
                List<BenchmarkResult> localResults = new ArrayList<>();
                localResults.addAll(handleBenchmarkPermutationOnly(image));
                localResults.addAll(handleBenchmarkDiffusionOnly(image));
                localResults.addAll(handleBenchmarkFull(image));
                allResults.addAll(localResults);
                AppLogger.info("Бенчмарк для файла " + file.getName() + " успешно завершен.");
            } catch (Throwable t) {
                AppLogger.error("Ошибка при бенчмарке файла: " + file.getName(), t);
                System.err.println("Ошибка при бенчмарке файла " + file.getName() + ": " + t.getMessage());
            }
        }

        try {
            String csvBaseName = inputFile.isDirectory() ? inputFile.getName() : "single_image";
            CsvExporter.export(allResults, "benchmark_" + csvBaseName);
            AppLogger.info("Бенчмарк завершен. Результаты сохранены в CSV.");
        } catch (IOException e) {
            AppLogger.error("Не удалось сохранить CSV", e);
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