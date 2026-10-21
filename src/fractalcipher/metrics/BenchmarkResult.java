package fractalcipher.metrics;

// Вернулись к двум именам (permutationName + diffusionName) вместо
// одного cipherName — потому что больше нет отдельного класса на
// каждую комбинацию, сравниваем именно ПАРЫ стратегий.
public class BenchmarkResult {
    public final String imageName;
    public final String permutationName;
    public final String diffusionName;
    public final long encryptionTimeMs;
    public final double correlation;
    public final double entropy;
    public final double npcr;
    public final double uaci;
    public final double mse;

    public BenchmarkResult(String imageName,String permutationName, String diffusionName, long encryptionTimeMs,
                           double correlation, double entropy, double npcr, double uaci, double mse) {
        this.imageName = imageName;
        this.permutationName = permutationName;
        this.diffusionName = diffusionName;
        this.encryptionTimeMs = encryptionTimeMs;
        this.correlation = correlation;
        this.entropy = entropy;
        this.npcr = npcr;
        this.uaci = uaci;
        this.mse = mse;
    }

    // Метод для создания строки CSV
    public String toCsvRow() {
        // ВАЖНО: Locale.US гарантирует, что дробные числа будут с точкой, а не с запятой
        return String.format(java.util.Locale.US, "%s,%s,%s,%d,%.4f,%.4f,%.2f,%.2f,%.4f",
                imageName, permutationName, diffusionName,
                encryptionTimeMs, correlation, entropy, npcr, uaci, mse);
    }


    @Override
    public String toString() {
        return String.format("%-12s + %-10s | %6d ms | corr=%7.4f | entropy=%.4f | NPCR=%6.2f%% | UACI=%6.2f%% | MSE=%.4f",
                permutationName, diffusionName, encryptionTimeMs, correlation, entropy, npcr, uaci, mse);
    }
}