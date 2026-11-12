package fractalcipher.metrics;

import fractalcipher.domain.EncryptionMode;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.model.KeyDerivation;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.diffusion.NullDiffusion;
import fractalcipher.model.permutation.IdentityPermutation;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.exception.EncryptionFailedException;
import fractalcipher.exception.ErrorHandler;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Запускает три исследовательские серии бенчмарка для одного изображения.
 * <p>
 * Каждая серия перебирает свою «ось» алгоритмов и фиксирует остальные:
 * </p>
 * <ul>
 *   <li>{@link #runPermutationOnly} — все перестановки × {@code null}-диффузия,
 *       режим {@link EncryptionMode#PERMUTATION_ONLY}. Оценивает вклад
 *       перестановки в разрушение пространственных связей.</li>
 *   <li>{@link #runDiffusionOnly} — identity-перестановка × все диффузии,
 *       режим {@link EncryptionMode#DIFFUSION_ONLY}. Оценивает вклад
 *       хаотических карт по NPCR/UACI/энтропии.</li>
 *   <li>{@link #runFull} — все комбинации × все комбинации, режим
 *       {@link EncryptionMode#FULL}. Итоговая криптостойкость.</li>
 * </ul>
 * <p>
 * Параллелизм — на уровне пар «перестановка + диффузия» через
 * {@code parallelStream}. Внутри {@link FractalChaosCipher} параллелизм
 * выключен (там не передаётся {@code isParallel}), чтобы избежать
 * борьбы за ядра.
 * </p>
 * <p>
 * Для воспроизводимости используется фиксированный {@link #MASTER_KEY}
 * (32 байта {@code 0..31}). Все seed-ы параметров выводятся из него
 * через {@link KeyDerivation} с доменной сепарацией — прогон на другой
 * машине даст те же числа.
 * </p>
 *
 * @author FractalCipher Team
 * @see BenchmarkResult
 * @see ImageMetrics
 */
public class BenchmarkRunner {

    /**
     * Фиксированный 256-битный мастер-ключ (32 байта).
     * <p>
     * Заполняется предсказуемыми значениями {@code 0..31} — так бенчмарк
     * воспроизводим: одинаковые параметры дают одинаковые шифротексты
     * и, следовательно, одинаковые метрики на любой машине.
     * </p>
     * <p>
     * <b>Не использовать в реальном шифровании</b> — ключ публичный
     * и одинаковый во всех прогонах.
     * </p>
     */
    private static final byte[] MASTER_KEY = new byte[32];
    static {
        // Заполняем массив предсказуемыми значениями (0, 1, 2...31)
        for (int i = 0; i < 32; i++) {
            MASTER_KEY[i] = (byte) i;
        }
    }

    /** Утилитарный класс — инстанцирование не нужно. */
    private BenchmarkRunner() {}

    /**
     * СЕРИЯ 1: только перестановка.
     * <p>
     * Перебирает <b>все</b> зарегистрированные фракталы, диффузия всегда
     * {@code null}, режим — {@link EncryptionMode#PERMUTATION_ONLY}.
     * CBC выключен, S-боксы не используются.
     * </p>
     * <p>
     * Цель — сравнить фракталы по тому, насколько хорошо они разрушают
     * пространственную корреляцию (поле {@code correlation} в
     * {@link BenchmarkResult}).
     * </p>
     *
     * @param pf            фабрика перестановок (содержит все фракталы)
     * @param originalImage исходное изображение
     * @return список результатов по одному на каждый фрактал
     */
    public static List<BenchmarkResult> runPermutationOnly(PermutationSourceFactory pf,
                                                           Image originalImage) {
        // Создаём фабрику диффузии ТОЛЬКО с null — никаких logistic/henon
        DiffusionSourceFactory nullOnlyFactory = new DiffusionSourceFactory() {{
            // Очищаем всё, что зарегистрировано в конструкторе
            clear();
            register(new NullDiffusion());
        }};

        return run(
                pf.availableNames(),           // все фракталы
                nullOnlyFactory.availableNames(), // только "null"
                pf, nullOnlyFactory,
                originalImage,
                EncryptionMode.PERMUTATION_ONLY
        );
    }

    /**
     * СЕРИЯ 2: только диффузия.
     * <p>
     * Перебирает <b>все</b> зарегистрированные источники диффузии,
     * перестановка всегда identity, режим — {@link EncryptionMode#DIFFUSION_ONLY}.
     * CBC выключен.
     * </p>
     * <p>
     * Цель — сравнить хаотические карты по NPCR/UACI/энтропии: насколько
     * сильно они меняют значения пикселей (в отличие от серии 1, которая
     * меняет только их позиции).
     * </p>
     *
     * @param df            фабрика диффузий (содержит все карты)
     * @param originalImage исходное изображение
     * @return список результатов по одному на каждую карту
     */
    public static List<BenchmarkResult> runDiffusionOnly(DiffusionSourceFactory df,
                                                         Image originalImage) {
        // Создаём фабрику перестановок ТОЛЬКО с identity
        PermutationSourceFactory identityOnlyFactory = new PermutationSourceFactory() {{
            clear();
            register(new IdentityPermutation());
        }};

        return run(
                identityOnlyFactory.availableNames(), // только "identity"
                df.availableNames(),                  // все диффузии
                identityOnlyFactory, df,
                originalImage,
                EncryptionMode.DIFFUSION_ONLY
        );
    }

    /**
     * СЕРИЯ 3: полная схема.
     * <p>
     * Перебирает все комбинации перестановок и диффузий, режим —
     * {@link EncryptionMode#FULL} (перестановка + диффузия + два прохода
     * CBC с S-боксами). Оценивает итоговую криптостойкость.
     * </p>
     *
     * @param pf            фабрика перестановок
     * @param df            фабрика диффузий
     * @param originalImage исходное изображение
     * @return список результатов по одному на каждую пару
     */
    public static List<BenchmarkResult> runFull(PermutationSourceFactory pf,
                                                DiffusionSourceFactory df,
                                                Image originalImage) {
        return run(
                pf.availableNames(),
                df.availableNames(),
                pf, df,
                originalImage,
                EncryptionMode.FULL
        );
    }

    /**
     * Общий движок всех трёх серий.
     * <p>
     * Для каждой пары «перестановка + диффузия»:
     * </p>
     * <ol>
     *   <li>Выводит все seed-ы из {@link #MASTER_KEY} с доменной сепарацией
     *       ({@code "permutation:<name>"}, {@code "diffusion:<name>:x0"} и т.д.).</li>
     *   <li>Шифрует оригинал, замеряет время.</li>
     *   <li>Шифрует модифицированную копию (один пиксель инвертирован) —
     *       нужна для NPCR и UACI.</li>
     *   <li>Расшифровывает первый шифротекст — нужна для MSE.</li>
     *   <li>Считает метрики и добавляет {@link BenchmarkResult} в список.</li>
     * </ol>
     * <p>
     * Пары обходятся параллельно через {@code parallelStream}.
     * Исключения в отдельной паре не прерывают весь прогон — пара просто
     * пропускается с сообщением в консоль (например, если для конкретных
     * параметров траектория карты разошлась).
     * </p>
     *
     * @param permutationNames  список имён перестановок (обычно — все)
     * @param diffusionNames    список имён диффузий (обычно — все)
     * @param permutationFactory фабрика, из которой достаются источники
     * @param diffusionFactory   фабрика, из которой достаются источники
     * @param originalImage      исходное изображение
     * @param mode               режим: какие этапы включать
     * @return потокобезопасный список результатов
     */
    private static List<BenchmarkResult> run(Iterable<String> permutationNames,
                                             Iterable<String> diffusionNames,
                                             PermutationSourceFactory permutationFactory,
                                             DiffusionSourceFactory diffusionFactory,
                                             Image originalImage,
                                             EncryptionMode mode) {
        Image modifiedImage = withOnePixelFlipped(originalImage);
        List<String> permutations = new ArrayList<>(permutationFactory.availableNames());
        List<String> diffusions = new ArrayList<>(diffusionFactory.availableNames());
        //Делаем список всех пар
        List<String[]> pairs = new ArrayList<>();
        for(String p : permutations){
            for(String d : diffusions) {
                pairs.add(new String[]{p, d});
            }
        }
        // Потокобезопасный список для сбора результатов
        List<BenchmarkResult> results = java.util.Collections.synchronizedList(new ArrayList<>());
        pairs.parallelStream().forEach(pair -> {
            String permutationName = pair[0];
            String diffusionName = pair[1];
            try {
                // Доменная сепарация: префикс в строке гарантирует, что
                // разные параметры не пересекутся даже при одинаковых именах
                long permSeed = KeyDerivation.deriveLong(
                        MASTER_KEY, "permutation:" + permutationName);
                double x0 = KeyDerivation.deriveDouble(
                        MASTER_KEY, "diffusion:" + diffusionName + ":x0");
                long sboxSeed = KeyDerivation.deriveLong(
                        MASTER_KEY, "diffusion:" + diffusionName + ":sbox");
                long sboxCbc1 = KeyDerivation.deriveLong(
                        MASTER_KEY, "diffusion:" + diffusionName + ":cbc1");
                long sboxCbc2 = KeyDerivation.deriveLong(
                        MASTER_KEY, "diffusion:" + diffusionName + ":cbc2");

                Map<String, Double> permParams = new HashMap<>();
                permParams.put("seed", (double) permSeed);

                Map<String, Double> diffParams = new HashMap<>();
                diffParams.put("x0", x0);
                diffParams.put("sboxSeed", (double) sboxSeed);
                diffParams.put("sboxCbc1", (double) sboxCbc1);
                diffParams.put("sboxCbc2", (double) sboxCbc2);

                long start = System.currentTimeMillis();

                FractalChaosCipher cipher = new FractalChaosCipher(
                        "bench", permutationName + "+" + diffusionName,
                        permutationFactory.get(permutationName),
                        diffusionFactory.get(diffusionName),
                        permParams, diffParams, mode);
                cipher.setImageInput(originalImage);
                cipher.encrypt();
                Image encrypted = cipher.getImageOutput();
                long elapsed = System.currentTimeMillis() - start;

                // Вторая шифровка — на изображении с инвертированным пикселем.
                // Отдельный объект шифра, потому что после encrypt()/decrypt()
                // состояние объекта «загрязнено» (imageOutput перезаписан).
                FractalChaosCipher cipherForModified = new FractalChaosCipher(
                        "bench", permutationName + "+" + diffusionName,
                        permutationFactory.get(permutationName),
                        diffusionFactory.get(diffusionName),
                        permParams, diffParams, mode);
                cipherForModified.setImageInput(modifiedImage);
                cipherForModified.encrypt();
                Image encryptedModified = cipherForModified.getImageOutput();

                // --- 3. РАСШИФРОВКА (для проверки MSE) ---
                // Используем тот же шифр, просто меняем вход на зашифрованное изображение
                cipher.setImageInput(encrypted);
                cipher.decrypt();
                Image decrypted = cipher.getImageOutput();

                // --- 4. СЧЕТ МЕТРИК ---
                double mseValue = ImageMetrics.mse(originalImage.getImage(), decrypted.getImage());

                results.add(new BenchmarkResult(
                        originalImage.getName(),
                        originalImage.isBlackAndWhite(),
                        originalImage.getResolution(),
                        permutationName, diffusionName, elapsed,
                        ImageMetrics.correlationCoefficient(encrypted.getImage()),
                        ImageMetrics.entropy(encrypted.getImage()),
                        ImageMetrics.npcr(encrypted.getImage(), encryptedModified.getImage()),
                        ImageMetrics.uaci(encrypted.getImage(), encryptedModified.getImage()),
                        mseValue // <-- ДОБАВЛЕНО
                ));
            } catch (Exception e) {
                // Одна неудачная пара не должна валить всю серию
                System.out.println("[Пропущено] " + permutationName + " + " + diffusionName + ": " + e.getMessage());
            }
        });
        return results;
    }

    /**
     * Создаёт копию изображения с инвертированным младшим битом пикселя
     * в позиции {@code (0, 0)}.
     * <p>
     * Используется для NPCR/UACI: лавинный эффект проверяется именно так —
     * изменить ровно один пиксель во входе и посмотреть, насколько
     * изменится шифротекст.
     * </p>
     * <p>
     * Копия создаётся в новом {@link BufferedImage}, чтобы не мутировать
     * исходное изображение (оно {@link Image} иммутабельно снаружи, но
     * внутри {@code BufferedImage} — изменяемый объект).
     * </p>
     *
     * @param image исходное изображение
     * @return новое {@link Image} с инвертированным пикселем
     */
    private static Image withOnePixelFlipped(Image image) {
        BufferedImage buffered = image.getImage();
        int width = buffered.getWidth();
        int height = buffered.getHeight();

        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, width, height, buffered.getRGB(0, 0, width, height, null, 0, width), 0, width);

        int pixel = copy.getRGB(0, 0);
        copy.setRGB(0, 0, pixel ^ 0x00000001);

        return new Image(image.getId() + "_flip", image.getName(), copy, image.getSize(), image.getFileType());
    }
}