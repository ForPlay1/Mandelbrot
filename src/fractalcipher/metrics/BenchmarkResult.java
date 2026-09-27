package fractalcipher.metrics;

// Простой класс-контейнер (data holder): хранит результат ОДНОГО
// прогона бенчмарка — одна комбинация "источник перестановки +
// источник диффузии" и посчитанные для неё метрики.
// Полей нет смысла делать приватными с геттерами — это чисто
// структура данных, а не объект с поведением.
public class BenchmarkResult {
    public final String permutationName;
    public final String diffusionName;
    public final long encryptionTimeMs; // сколько миллисекунд заняло шифрование

    // Метрики из ImageMetrics, посчитанные для этой комбинации
    public final double correlation;
    public final double entropy;
    public final double npcr;
    public final double uaci;

    public BenchmarkResult(String permutationName, String diffusionName, long encryptionTimeMs,
                           double correlation, double entropy, double npcr, double uaci) {
        this.permutationName = permutationName;
        this.diffusionName = diffusionName;
        this.encryptionTimeMs = encryptionTimeMs;
        this.correlation = correlation;
        this.entropy = entropy;
        this.npcr = npcr;
        this.uaci = uaci;
    }

    // toString переопределён, чтобы ConsoleView могла просто сделать
    // println(result) и получить готовую читаемую строку таблицы,
    // не разбираясь в полях класса.
    @Override
    public String toString() {
        // %-12s / %-10s — выравнивание по левому краю (для имён),
        // %6d / %7.4f и т.д. — выравнивание по правому краю с
        // фиксированной шириной, чтобы столбцы совпадали построчно.
        return String.format("%-12s + %-10s | %6d ms | corr=%7.4f | entropy=%.4f | NPCR=%6.2f%% | UACI=%6.2f%%",
                permutationName, diffusionName, encryptionTimeMs, correlation, entropy, npcr, uaci);
    }
}