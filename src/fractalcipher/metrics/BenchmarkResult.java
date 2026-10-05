package fractalcipher.metrics;

// Вернулись к двум именам (permutationName + diffusionName) вместо
// одного cipherName — потому что больше нет отдельного класса на
// каждую комбинацию, сравниваем именно ПАРЫ стратегий.
public class BenchmarkResult {
    public final String permutationName;
    public final String diffusionName;
    public final long encryptionTimeMs;
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

    @Override
    public String toString() {
        return String.format("%-12s + %-10s | %6d ms | corr=%7.4f | entropy=%.4f | NPCR=%6.2f%% | UACI=%6.2f%%",
                permutationName, diffusionName, encryptionTimeMs, correlation, entropy, npcr, uaci);
    }
}