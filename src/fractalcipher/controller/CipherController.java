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

/**
 * Контроллер, управляющий бизнес-логикой шифрования, расшифровки и бенчмарка.
 * <p>
 * Это связующее звено между View (интерфейсом) и Model (шифрами).
 * Контроллер принимает выбор пользователя из интерфейса, выполняет чтение/запись файлов,
 * управляет генерацией ключей и распределяет задачи по потокам (контекстный параллелизм).
 * </p>
 */
public class CipherController {

    private final PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
    private final DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();
    private final ImageView view = new ConsoleView();

    /**
     * Главная точка входа для бизнес-логики.
     * Вызывается из Main после того, как пользователь нажал кнопку "Запустить".
     *
     * @param selection Словарь, содержащий выбор пользователя: режим, методы, файлы и пути.
     */
    public void processSelection(Map<Class<?>, Object> selection) {
        CipherMode mode = (CipherMode) selection.get(CipherMode.class);
        PermutationMode perm = (PermutationMode) selection.get(PermutationMode.class);
        DiffusionMode diff = (DiffusionMode) selection.get(DiffusionMode.class);
        File input = (File) selection.get(File.class);
        Path out = (Path) selection.get(Path.class);

        File output = (out != null) ? out.toFile() : null;

        // Защита от некорректного вызова
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
            // Маршрутизация задачи в зависимости от выбранного режима
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
            // Централизованная обработка ошибок
            String safeMsg = ErrorHandler.handle(t);
            AppLogger.error("--- Операция аварийно завершена ---", t);
            view.showError(safeMsg);
        }
    }

    /**
     * Шифрует или расшифровывает одно изображение.
     * <p>
     * Для одиночного файла включается <b>внутренний параллелизм</b> (isParallel = 1.0),
     * чтобы фрактал и хаотическая карта считались на всех ядрах процессора.
     * </p>
     *
     * @param mode            Режим (ENCRYPT или DECRYPT).
     * @param permutationName Имя источника перестановки (например, "mandelbrot").
     * @param diffusionName   Имя источника диффузии (например, "logistic").
     * @param inputFile       Исходный файл изображения.
     * @param image           Загруженное изображение.
     * @param outputDir       Папка для сохранения (если null, сохраняется рядом с исходным).
     */
    private void runSingle(CipherMode mode, String permutationName, String diffusionName,
                           File inputFile, Image image, File outputDir) {
        boolean isDecrypt = (mode == CipherMode.DECRYPT);
        String operation = isDecrypt ? "Расшифровка" : "Шифрование";
        AppLogger.info(operation + " файла: " + inputFile.getName() + " [" + permutationName + " + " + diffusionName + "]");

        File targetFile;
        File keyFile;
        byte[] masterKey; // 256-битный ключ (32 байта)

        // Определяем папку для сохранения (если не выбрана, берем папку исходника)
        File saveDir = (outputDir != null) ? outputDir : inputFile.getParentFile();

        if (isDecrypt) {
            // --- РЕЖИМ РАСШИФРОВКИ ---
            targetFile = inputFile;
            keyFile = KeyManager.getKeyFile(targetFile);
            if (!keyFile.exists()) {
                throw new InvalidInputException("Файл ключа не найден: " + keyFile.getAbsolutePath());
            }
            try {
                EncryptionKey key = KeyManager.loadKey(keyFile);
                masterKey = key.masterKey;
                permutationName = key.permutationSourceName;
                diffusionName = key.diffusionSourceName;
                AppLogger.info("Ключ успешно загружен из: " + keyFile.getName());
            } catch (Exception e) {
                AppLogger.error("Ошибка чтения ключа: " + keyFile.getName(), e);
                throw new RuntimeException("Ошибка чтения ключа: " + e.getMessage());
            }
        } else {
            // --- РЕЖИМ ШИФРОВАНИЯ ---
            String originalName = inputFile.getName();
            String baseNameStr = CipherUtils.baseName(originalName);

            // Сохраняем зашифрованный файл в выбранную папку saveDir
            targetFile = new File(saveDir, baseNameStr + CipherUtils.ENCRYPTED_SUFFIX + ".png");
            keyFile = KeyManager.getKeyFile(targetFile);

            // Генерация 256-битного случайного ключа (32 байта)
            masterKey = new byte[32];
            new SecureRandom().nextBytes(masterKey);
            EncryptionKey newKey = new EncryptionKey(masterKey, permutationName, diffusionName);
            try {
                KeyManager.saveKey(newKey, keyFile);
                AppLogger.info("Сгенерирован и сохранен новый ключ: " + keyFile.getName());
            } catch (IOException e) {
                AppLogger.error("Не удалось сохранить ключ: " + keyFile.getName(), e);
                throw new RuntimeException("Не удалось сохранить ключ: " + e.getMessage());
            }
        }

        // Генерируем параметры из мастер-ключа (byte[])
        Map<String, Double> permParams = CipherUtils.buildPermParams(masterKey, permutationName);
        Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterKey, diffusionName);

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
    }

    // === ПАКЕТНОЕ ШИФРОВАНИЕ ПАПКИ ===
    /**
     * Шифрует все изображения в папке.
     * <p>
     * Использует <b>внешний параллелизм</b> (parallelStream по файлам).
     * Внутренний параллелизм фракталов принудительно отключен (isParallel = 0.0),
     * чтобы избежать конфликта потоков (Thread Starvation).
     * </p>
     */
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

        // Атомарные счетчики для потокобезопасного подсчета
        AtomicInteger ok = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // КОНТЕКСТНЫЙ ПАРАЛЛЕЛИЗМ: Параллелим обработку САМИХ ФАЙЛОВ
        Arrays.stream(files).parallel().forEach(file -> {
            try {
                Image input = Image.loadFromFile(file.getName(), file.getName(), file);
                // Генерация уникального 256-битного ключа для каждого файла
                byte[] masterKey = new byte[32];
                new SecureRandom().nextBytes(masterKey);

                EncryptionKey key = new EncryptionKey(masterKey, permutationName, diffusionName);
                File targetImage = new File(outputDir, CipherUtils.baseName(file.getName()) + CipherUtils.ENCRYPTED_SUFFIX + ".png");
                File targetKey = KeyManager.getKeyFile(targetImage);
                KeyManager.saveKey(key, targetKey);

                Map<String, Double> permParams = CipherUtils.buildPermParams(masterKey, permutationName);
                Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterKey, diffusionName);

                // ВЫКЛЮЧАЕМ внутренний параллелизм для фракталов
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
    /**
     * Расшифровывает все файлы {@code *_encrypted.png} в папке.
     * <p>
     * Аналогично пакетному шифрованию, использует параллелизм на уровне файлов.
     * Ключи (256-битные) загружаются из соответствующих файлов {@code .key}.
     * </p>
     */
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
            try {
                File keyFile = KeyManager.getKeyFile(file);
                if (!keyFile.exists()) {
                    throw new InvalidInputException("Ключ не найден: " + keyFile.getName());
                }
                EncryptionKey key = KeyManager.loadKey(keyFile);

                Map<String, Double> permParams = CipherUtils.buildPermParams(key.masterKey, key.permutationSourceName);
                Map<String, Double> diffParams = CipherUtils.buildDiffParams(key.masterKey, key.diffusionSourceName);

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
    /**
     * Запускает исследовательский бенчмарк для оценки метрик шифрования.
     * <p>
     * Бенчмарк последовательно перебирает файлы (если выбрана папка),
     * но внутри каждого файла {@link BenchmarkRunner} уже использует
     * параллельный перебор комбинаций алгоритмов.
     * </p>
     */
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

        // Последовательно по файлам, параллелизм уже внутри BenchmarkRunner
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

        // Сохранение результатов в CSV
        try {
            String csvBaseName = inputFile.isDirectory() ? inputFile.getName() : "single_image";
            CsvExporter.export(allResults, "benchmark_" + csvBaseName);
            AppLogger.info("Бенчмарк завершен. Результаты сохранены в CSV.");
        } catch (IOException e) {
            AppLogger.error("Не удалось сохранить CSV", e);
        }

        view.showBenchmarkResults(allResults);
    }

    // --- Методы делегирования ---
    // Эти методы просто вызывают соответствующие методы у переданного объекта Cipher.

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