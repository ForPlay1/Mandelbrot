package fractalcipher.metrics;

/**
 * Одна строка результата бенчмарка: комбинация «перестановка + диффузия»,
 * применённая к одному изображению, и измеренные метрики.
 * <p>
 * Поле {@link #blackAndWhite} позволяет при анализе сравнивать качество
 * алгоритмов отдельно на чёрно-белых и на цветных изображениях: не исключено,
 * что один и тот же шифр ведёт себя по-разному в зависимости от насыщенности
 * цветовых каналов.
 * </p>
 *
 * @author FractalCipher Team
 */
public class BenchmarkResult {
    /** Имя изображения, к которому применялась комбинация. */
    public final String imageName;

    /**
     * Признак «изображение в оттенках серого».
     * <p>
     * Берётся из {@link fractalcipher.domain.Image#isBlackAndWhite()} —
     * эвристика по каналам R/G/B (см. {@code Image.detectBlackAndWhite}).
     * </p>
     */
    public final boolean blackAndWhite;

    /** Имя источника перестановки (например, {@code "mandelbrot"}). */
    public final String permutationName;

    /** Имя источника диффузии (например, {@code "logistic"}). */
    public final String diffusionName;

    /** Время шифрования в миллисекундах. */
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
     * @param permutationName   имя источника перестановки
     * @param diffusionName     имя источника диффузии
     * @param encryptionTimeMs  время шифрования, мс
     * @param correlation       коэффициент корреляции
     * @param entropy           энтропия
     * @param npcr              NPCR, %
     * @param uaci              UACI, %
     * @param mse               MSE
     */
    public BenchmarkResult(String imageName,boolean blackAndWhite,String permutationName, String diffusionName, long encryptionTimeMs,
                           double correlation, double entropy, double npcr, double uaci, double mse) {
        this.imageName = imageName;
        this.blackAndWhite = blackAndWhite;
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
     * {@link java.util.Locale#US} гарантирует, что дробные числа будут
     * с точкой, а не с запятой (иначе в русской локали CSV распадётся
     * на лишние колонки). Флаг ч/б пишется как {@code true}/{@code false} —
     * Excel понимает его без настройки.
     * </p>
     *
     * @return строка без завершающего перевода строки
     */
    public String toCsvRow() {
        // ВАЖНО: Locale.US гарантирует, что дробные числа будут с точкой, а не с запятой
        return String.format(java.util.Locale.US,
                "%s,%s,%s,%s,%d,%.4f,%.4f,%.2f,%.2f,%.4f",
                imageName,
                blackAndWhite,
                permutationName, diffusionName,
                encryptionTimeMs, correlation, entropy, npcr, uaci, mse);
    }


    @Override
    public String toString() {
        return String.format(
                "%-12s + %-10s | %-5s | %6d ms | corr=%7.4f | entropy=%.4f | NPCR=%6.2f%% | UACI=%6.2f%% | MSE=%.4f",
                permutationName, diffusionName,
                blackAndWhite ? "B/W" : "RGB",
                encryptionTimeMs, correlation, entropy, npcr, uaci, mse);
    }
}