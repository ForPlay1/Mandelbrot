package fractalcipher.metrics;

/**
 * Одна строка результата бенчмарка: комбинация «перестановка + диффузия»,
 * применённая к одному изображению, и измеренные метрики.
 * <p>
 * Класс иммутабельный: все поля {@code final}, доступны напрямую
 * (без геттеров) — это DTO, а не объект с поведением, и открытые поля
 * делают код короче. Значения заполняются только в конструкторе.
 * </p>
 * <p>
 * Поле {@link #blackAndWhite} позволяет при анализе сравнивать качество
 * алгоритмов отдельно на чёрно-белых и на цветных изображениях: не исключено,
 * что один и тот же шифр ведёт себя по-разному в зависимости от насыщенности
 * цветовых каналов.
 * </p>
 *
 * @author FractalCipher Team
 * @see BenchmarkRunner
 * @see CsvExporter
 */
public class BenchmarkResult {

    /** Имя изображения, к которому применялась комбинация. */
    public final String imageName;

    /**
     * Признак «изображение в оттенках серого».
     * <p>
     * Берётся из {@link fractalcipher.domain.Image#isBlackAndWhite()} у
     * <b>оригинального</b> изображения (до шифрования) — эвристика по
     * каналам R/G/B, см. {@code Image.detectBlackAndWhite}.
     * </p>
     * <p>
     * Важно: брать флаг у шифротекста нельзя — после диффузии пиксели
     * становятся «цветными» по определению, и признак всегда был бы
     * {@code false}.
     * </p>
     */
    public final boolean blackAndWhite;

    /**
     * Разрешение изображения в формате {@code "ШИРИНАxВЫСОТА"}.
     * <p>
     * Берётся из {@link fractalcipher.domain.Image#getResolution()} у
     * оригинального изображения. Например, {@code "1920x1080"}.
     * </p>
     */
    public final String resolution;

    /**
     * Seed, из которого был сгенерирован мастер-ключ.
     * <p>
     * Используется для проверки стабильности: по одному и тому же
     * изображению и паре алгоритмов можно сравнить метрики при разных
     * {@code keySeed} и оценить разброс (дисперсию).
     * </p>
     */
    public final int keySeed;

    /** Имя источника перестановки (например, {@code "mandelbrot"}). */
    public final String permutationName;

    /** Имя источника диффузии (например, {@code "logistic"}). */
    public final String diffusionName;

    /** Время шифрования в миллисекундах (замер только этапа шифрования, без расшифровки). */
    public final long encryptionTimeMs;

    /** Коэффициент корреляции соседних пикселей шифротекста (идеал — около 0). */
    public final double correlation;

    /** Энтропия Шеннона шифротекста (идеал — около 8 для 8-битного канала). */
    public final double entropy;

    /** NPCR между двумя шифротекстами с разницей в 1 пиксель (идеал — > 99.6%). */
    public final double npcr;

    /** UACI между теми же шифротекстами (идеал — около 33.4%). */
    public final double uaci;

    /** MSE между оригиналом и расшифрованным изображением (идеал — 0, без потерь). */
    public final double mse;

    /**
     * Создаёт результат бенчмарка.
     *
     * @param imageName         имя изображения
     * @param blackAndWhite     {@code true}, если изображение в оттенках серого
     * @param resolution        разрешение в формате {@code "WxH"}
     * @param keySeed           seed, из которого сгенерирован ключ
     * @param permutationName   имя источника перестановки
     * @param diffusionName     имя источника диффузии
     * @param encryptionTimeMs  время шифрования, мс
     * @param correlation       коэффициент корреляции (от −1 до 1; идеал — 0)
     * @param entropy           энтропия Шеннона (0..8 для 8-битного канала)
     * @param npcr              NPCR, % (0..100)
     * @param uaci              UACI, % (0..100)
     * @param mse               MSE (0 — идеальное восстановление)
     */
    public BenchmarkResult(String imageName,boolean blackAndWhite,String resolution,int keySeed,String permutationName, String diffusionName, long encryptionTimeMs,
                           double correlation, double entropy, double npcr, double uaci, double mse) {
        this.imageName = imageName;
        this.blackAndWhite = blackAndWhite;
        this.resolution = resolution;
        this.keySeed = keySeed;
        this.permutationName = permutationName;
        this.diffusionName = diffusionName;
        this.encryptionTimeMs = encryptionTimeMs;
        this.correlation = correlation;
        this.entropy = entropy;
        this.npcr = npcr;
        this.uaci = uaci;
        this.mse = mse;
    }

    /**
     * Формирует строку CSV.
     * <p>
     * Колонки строго в порядке заголовка
     * ({@code ImageName,BlackAndWhite,Resolution,KeySeed,Permutation,Diffusion,
     * TimeMs,Correlation,Entropy,NPCR,UACI,MSE}).
     * </p>
     *
     * @return строка без завершающего перевода строки
     */
    public String toCsvRow() {
        return String.format(java.util.Locale.US,
                "%s,%s,%s,%d,%s,%s,%d,%.4f,%.4f,%.2f,%.2f,%.4f",
                imageName,
                blackAndWhite,
                resolution,
                keySeed,
                permutationName, diffusionName,
                encryptionTimeMs, correlation, entropy, npcr, uaci, mse);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "key=%-2d | %-12s + %-10s | %-3s %-9s | %6d ms | corr=%7.4f | entropy=%.4f | NPCR=%6.2f%% | UACI=%6.2f%% | MSE=%.4f",
                keySeed,
                permutationName, diffusionName,
                blackAndWhite ? "B/W" : "RGB",
                resolution,
                encryptionTimeMs, correlation, entropy, npcr, uaci, mse);
    }
}