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
 *
 * <p><b>Политика перезаписи.</b> В словаре {@code selection} может присутствовать ключ
 * {@code Boolean.class}:
 * <ul>
 *   <li>{@code Boolean.TRUE} или отсутствие ключа — перезаписывать существующие файлы;</li>
 *   <li>{@code Boolean.FALSE} — пропускать файлы, чьи целевые имена уже существуют.</li>
 * </ul>
 * Отмена операции обрабатывается во View — до контроллера словарь в этом случае не доходит.
 * </p>
 */
public class CipherController {

    private final PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
    private final DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();
    private final ImageView view = new ConsoleView();

    /** Количество разных ключей для проверки стабильности. */
    private static final int BENCHMARK_KEYS = 5;

    /**
     * Главная точка входа для бизнес-логики.
     * Вызывается из Main после того, как пользователь нажал кнопку "Запустить".
     *
     * @param selection Словарь, содержащий выбор пользователя: режим, методы, файлы и пути.
     *                  Опционально {@code Boolean.class} — политика перезаписи.
     */
    public void processSelection(Map<Class<?>, Object> selection) {
        CipherMode mode = (CipherMode) selection.get(CipherMode.class);
        PermutationMode perm = (PermutationMode) selection.get(PermutationMode.class);
        DiffusionMode diff = (DiffusionMode) selection.get(DiffusionMode.class);
        File input = (File) selection.get(File.class);
        Path out = (Path) selection.get(Path.class);

        // Набор исходных файлов, которые нужно ПРОПУСТИТЬ (решение пользователя)
        Set<File> skipFiles = new HashSet<>();
        if (selection.get(File[].class) instanceof File[] arr) {
            skipFiles.addAll(Arrays.asList(arr));
        }

        File output = (out != null) ? out.toFile() : null;

        // Защита от некорректного вызова
        if (mode == null || input == null) {
            AppLogger.warning("Запуск отменен: не выбран режим или входной файл.");
            System.out.println("Режим или входной файл не выбраны — выходим.");
            return;
        }

        AppLogger.info("--- Запуск операции ---");
        AppLogger.info("Режим: " + mode.name() + ", Вход: " + input.getAbsolutePath());
        if (output != null) {
            AppLogger.info("Выходная папка: " + output.getAbsolutePath());
        }
        try {
            switch (mode) {
                case BENCRYPT ->
                        runBatchEncrypt(perm.getName(), diff.getName(), input, output, skipFiles);
                case BENDECRYPT ->
                        runBatchDecrypt(input, output, skipFiles);
                case BENCHMARK ->
                        runBenchmark(input);
                case ENCRYPT, DECRYPT -> {
                    Image image = Image.loadFromFile(input.getName(), input.getName(), input);
                    runSingle(mode,
                            perm != null ? perm.getName() : null,
                            diff != null ? diff.getName() : null,
                            input, image, output, skipFiles);
                }
            }
            AppLogger.info("--- Операция успешно завершена ---");
        } catch (Throwable t) {
            String safeMsg = ErrorHandler.handle(t);
            AppLogger.error("--- Операция аварийно завершена ---", t);
            view.showError(safeMsg);
        }
    }

    // ==================================================================
    //  ОДИНОЧНЫЙ ФАЙЛ
    // ==================================================================

    /**
     * Шифрует или расшифровывает одно изображение.
     * <p>
     * Для одиночного файла включается <b>внутренний параллелизм</b> (isParallel = 1.0),
     * чтобы фрактал и хаотическая карта считались на всех ядрах процессора.
     * </p>
     *
     * <p><b>Имена файлов.</b>
     * <ul>
     *   <li>Шифрование: {@code "photo.png"} → {@code "photo_png_encrypted.png"} + {@code "photo_png_encrypted.key"}.
     *       Расширение входит в имя — файлы {@code photo.png} и {@code photo.jpg} больше не конфликтуют.</li>
     *   <li>Расшифровка: {@code "photo_png_encrypted.png"} → {@code "photo_png_decrypted.png"},
     *       ключ ищется рядом с входным (зашифрованным) файлом.</li>
     * </ul>
     * </p>
     */
    private void runSingle(CipherMode mode, String permutationName, String diffusionName,
                           File inputFile, Image image, File outputDir, Set<File> skipFiles) {
        boolean isDecrypt = (mode == CipherMode.DECRYPT);
        String operation = isDecrypt ? "Расшифровка" : "Шифрование";
        AppLogger.info(operation + " файла: " + inputFile.getName()
                + " [" + permutationName + " + " + diffusionName + "]");

        // Определяем папку для сохранения (если не выбрана — папка исходника)
        File saveDir = (outputDir != null) ? outputDir : inputFile.getParentFile();

        // === 1. Считаем имя цели одинаково для обоих режимов ===
        File targetFile;
        if (isDecrypt) {
            String base = CipherUtils.baseName(inputFile.getName()).replace(CipherUtils.ENCRYPTED_SUFFIX, "");
            targetFile = new File(saveDir, base + "_decrypted.png");
        } else {
            targetFile = new File(saveDir, CipherUtils.safeStem(inputFile.getName()) + CipherUtils.ENCRYPTED_SUFFIX + ".png");
        }

        // 2. Пользователь нажал «Пропустить» для этого файла
        if (skipFiles.contains(inputFile)) {
            AppLogger.info("Пропущено (по выбору пользователя): " + inputFile.getName());
            System.out.println("Пропущено: " + targetFile.getName());
            return;
        }

        // === 3. Ключ: для decrypt — от входного (зашифрованного), для encrypt — от цели ===
        File keyFile;
        byte[] masterKey;

        if (isDecrypt) {
            keyFile = KeyManager.getKeyFile(inputFile);
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
            keyFile = KeyManager.getKeyFile(targetFile);
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

        // === 4. Шифрование / расшифровка ===
        Map<String, Double> permParams = CipherUtils.buildPermParams(masterKey, permutationName);
        Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterKey, diffusionName);

        // Внутренний параллелизм — для одного файла имеет смысл
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

        // === 5. Запись в targetFile (имя уже посчитано выше) ===
        try {
            ImageIO.write(result.getImage(), "png", targetFile);
            AppLogger.info((isDecrypt ? "Расшифровано: " : "Зашифровано: ")
                    + targetFile.getName() + " (" + permutationName + " + " + diffusionName + ")");
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сохранения: " + e.getMessage());
        }
    }

    // ==================================================================
    //  ПАКЕТНОЕ ШИФРОВАНИЕ ПАПКИ
    // ==================================================================

    /**
     * Шифрует все изображения в папке.
     * <p>
     * Использует <b>внешний параллелизм</b> (parallelStream по файлам).
     * Внутренний параллелизм фракталов принудительно отключен (isParallel = 0.0),
     * чтобы избежать конфликта потоков (Thread Starvation).
     * </p>
     * <p>
     * Целевое имя строится через {@link CipherUtils#safeStem(String)}, поэтому
     * {@code photo.png} и {@code photo.jpg} из одной папки дают разные
     * {@code photo_png_encrypted.png} и {@code photo_jpg_encrypted.png}.
     * </p>
     */
    private void runBatchEncrypt(String permutationName, String diffusionName,
                                 File inputDir, File outputDir, Set<File> skipFiles) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(f -> f.isFile() && CipherUtils.isImageToEncrypt(f.getName()));
        if (files == null || files.length == 0) {
            AppLogger.warning("Пакетное шифрование отменено: в папке нет изображений.");
            view.showError("В папке нет изображений для шифрования.");
            return;
        }
        Arrays.sort(files);

        AppLogger.info("Начало пакетного шифрования. Файлов: " + files.length
                + " [" + permutationName + " + " + diffusionName + "]");
        long startTime = System.currentTimeMillis();

        AtomicInteger ok      = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);

        final Set<File> skipFinal = skipFiles; // для лямбды

        // Внешний параллелизм: параллелим обработку САМИХ ФАЙЛОВ
        Arrays.stream(files).parallel().forEach(file -> {
            File targetImage = new File(outputDir,
                    CipherUtils.safeStem(file.getName()) + CipherUtils.ENCRYPTED_SUFFIX + ".png");

            if (skipFinal.contains(file)) {
                skipped.incrementAndGet();
                AppLogger.info("Пропущено (по выбору пользователя): " + file.getName());
                return;
            }

            try {
                Image input = Image.loadFromFile(file.getName(), file.getName(), file);

                byte[] masterKey = new byte[32];
                new SecureRandom().nextBytes(masterKey);

                EncryptionKey key = new EncryptionKey(masterKey, permutationName, diffusionName);
                File targetKey = KeyManager.getKeyFile(targetImage);
                KeyManager.saveKey(key, targetKey);

                Map<String, Double> permParams = CipherUtils.buildPermParams(masterKey, permutationName);
                Map<String, Double> diffParams = CipherUtils.buildDiffParams(masterKey, diffusionName);

                // Внутренний параллелизм выключен — параллелизм уже на уровне файлов
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
        AppLogger.info(String.format(
                "Пакетное шифрование завершено за %d мс. Успешно: %d, Пропущено: %d, Ошибок: %d",
                duration, ok.get(), skipped.get(), failed.get()));

    }

    // ==================================================================
    //  ПАКЕТНАЯ РАСШИФРОВКА ПАПКИ
    // ==================================================================

    /**
     * Расшифровывает все файлы {@code *_encrypted.png} в папке.
     * <p>
     * Аналогично пакетному шифрованию, использует параллелизм на уровне файлов.
     * Ключи загружаются из соответствующих файлов {@code .key}, лежащих рядом
     * с входным (зашифрованным) изображением.
     * </p>
     */
    private void runBatchDecrypt(File inputDir, File outputDir, Set<File> skipFiles) {
        if (inputDir == null || outputDir == null) return;

        File[] files = inputDir.listFiles(
                f -> f.isFile() && f.getName().endsWith(CipherUtils.ENCRYPTED_SUFFIX + ".png"));
        if (files == null || files.length == 0) {
            AppLogger.warning("Пакетная расшифровка отменена: в папке нет зашифрованных изображений.");
            view.showError("В папке нет зашифрованных изображений (*_encrypted.png).");
            return;
        }
        Arrays.sort(files);

        AppLogger.info("Начало пакетной расшифровки. Файлов: " + files.length);
        long startTime = System.currentTimeMillis();

        AtomicInteger ok      = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);

        final Set<File> skipFinal= skipFiles;

        Arrays.stream(files).parallel().forEach(file -> {
            String decName = CipherUtils.baseName(file.getName())
                    .replace(CipherUtils.ENCRYPTED_SUFFIX, "") + "_decrypted.png";
            File targetImage = new File(outputDir, decName);

            if (skipFinal.contains(file)) {
                skipped.incrementAndGet();
                AppLogger.info("Пропущено (по выбору пользователя): " + file.getName());
                return;
            }

            try {
                File keyFile = KeyManager.getKeyFile(file);
                if (!keyFile.exists()) {
                    throw new InvalidInputException("Ключ не найден: " + keyFile.getName());
                }
                EncryptionKey key = KeyManager.loadKey(keyFile);

                Map<String, Double> permParams =
                        CipherUtils.buildPermParams(key.masterKey, key.permutationSourceName);
                Map<String, Double> diffParams =
                        CipherUtils.buildDiffParams(key.masterKey, key.diffusionSourceName);

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

                ImageIO.write(result.getImage(), "png", targetImage);

                AppLogger.info("Пакетная расшифровка [OK]: " + targetImage.getName());
                ok.incrementAndGet();
            } catch (Throwable t) {
                AppLogger.error("Пакетная расшифровка [FAIL]: " + file.getName(), t);
                failed.incrementAndGet();
            }
        });

        long duration = System.currentTimeMillis() - startTime;
        AppLogger.info(String.format(
                "Пакетная расшифровка завершена за %d мс. Успешно: %d, Пропущено: %d, Ошибок: %d",
                duration, ok.get(), skipped.get(), failed.get()));
    }

    // ==================================================================
    //  БЕНЧМАРК
    // ==================================================================

    /**
     * Запускает исследовательский бенчмарк.
     * <p>
     * <b>Проверка стабильности.</b> Бенчмарк прогоняется
     * {@value #BENCHMARK_KEYS} раз с разными мастер-ключами. Ключи
     * генерируются детерминированно от seed-а (0..N-1), чтобы результаты
     * были воспроизводимы, а seed записывается в CSV для анализа разброса.
     * </p>
     */
    private void runBenchmark(File inputFile) {
        List<File> imagesToProcess = new ArrayList<>();

        if (inputFile.isDirectory()) {
            File[] files = inputFile.listFiles(
                    f -> f.isFile() && CipherUtils.isImageToEncrypt(f.getName()));
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

        System.out.println("\n=== БЕНЧМАРК: изображений: " + imagesToProcess.size()
                + ", ключей: " + BENCHMARK_KEYS + " ===");
        AppLogger.info("Запуск бенчмарка. Изображений: " + imagesToProcess.size()
                + ", ключей: " + BENCHMARK_KEYS);

        List<BenchmarkResult> allResults = Collections.synchronizedList(new ArrayList<>());

        for (int seed = 0; seed < BENCHMARK_KEYS; seed++) {
            byte[] masterKey = makeKey(seed);
            AppLogger.info("Бенчмарк: используется ключ seed=" + seed);
            System.out.println("--- Ключ seed=" + seed + " ---");

            for (File file : imagesToProcess) {
                System.out.println("  Анализ: " + file.getName());
                try {
                    Image image = Image.loadFromFile(file.getName(), file.getName(), file);
                    List<BenchmarkResult> localResults = new ArrayList<>();
                    localResults.addAll(handleBenchmarkPermutationOnly(image, masterKey, seed));
                    localResults.addAll(handleBenchmarkDiffusionOnly(image, masterKey, seed));
                    localResults.addAll(handleBenchmarkFull(image, masterKey, seed));
                    allResults.addAll(localResults);
                    AppLogger.info("Бенчмарк для файла " + file.getName()
                            + " (seed=" + seed + ") успешно завершен.");
                } catch (Throwable t) {
                    AppLogger.error("Ошибка при бенчмарке файла: " + file.getName()
                            + " (seed=" + seed + ")", t);
                    System.err.println("Ошибка при бенчмарке файла " + file.getName()
                            + ": " + t.getMessage());
                }
            }
        }

        try {
            String csvBaseName = inputFile.isDirectory()
                    ? inputFile.getName() : "single_image";
            CsvExporter.export(allResults, "benchmark_" + csvBaseName);
            AppLogger.info("Бенчмарк завершен. Результаты сохранены в CSV.");
        } catch (IOException e) {
            AppLogger.error("Не удалось сохранить CSV", e);
        }

        view.showBenchmarkResults(allResults);
    }

    /**
     * Генерирует детерминированный 256-битный мастер-ключ по seed-у.
     * <p>
     * Одинаковый seed всегда даёт одинаковый ключ — это нужно для
     * воспроизводимости бенчмарка.
     * </p>
     */
    private static byte[] makeKey(int seed) {
        byte[] key = new byte[32];
        new java.util.Random(seed).nextBytes(key);
        return key;
    }

    // --- Методы делегирования (сигнатуры обновлены) ---

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

    public List<BenchmarkResult> handleBenchmarkPermutationOnly(Image image,
                                                                byte[] masterKey,
                                                                int keySeed) {
        return BenchmarkRunner.runPermutationOnly(permutationFactory, image, masterKey, keySeed);
    }

    public List<BenchmarkResult> handleBenchmarkDiffusionOnly(Image image,
                                                              byte[] masterKey,
                                                              int keySeed) {
        return BenchmarkRunner.runDiffusionOnly(diffusionFactory, image, masterKey, keySeed);
    }

    public List<BenchmarkResult> handleBenchmarkFull(Image image,
                                                     byte[] masterKey,
                                                     int keySeed) {
        return BenchmarkRunner.runFull(permutationFactory, diffusionFactory, image, masterKey, keySeed);
    }
}