package fractalcipher.metrics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Сохраняет результаты бенчмарка в CSV-файл в папке {@code benchmarks/}.
 * <p>
 * Папка создаётся автоматически при первом вызове. Имя файла содержит
 * отметку времени, поэтому повторные запуски не перезаписывают
 * предыдущие результаты.
 * </p>
 * <p>
 * <b>Формат CSV</b> (порядок колонок — контракт между {@link #export}
 * и {@link BenchmarkResult#toCsvRow()}):
 * </p>
 * <pre>
 * ImageName,BlackAndWhite,Resolution,KeySeed,Permutation,Diffusion,
 * TimeMs,Correlation,Entropy,NPCR,UACI,MSE
 * </pre>
 *
 * @author FractalCipher Team
 * @see BenchmarkResult
 */
public class CsvExporter {

    /** Утилитарный класс — инстанцирование не нужно. */
    private CsvExporter() {}

    /**
     * Заголовок CSV. <b>Должен совпадать по количеству и порядку колонок
     * с</b> {@link BenchmarkResult#toCsvRow()}.
     */
    private static final String HEADER =
            "ImageName,BlackAndWhite,Resolution,KeySeed,Permutation,Diffusion,"
                    + "TimeMs,Correlation,Entropy,NPCR,UACI,MSE";

    /**
     * Сохраняет список результатов в новый CSV-файл.
     *
     * @param results      список результатов
     * @param baseFileName базовое имя без расширения
     * @throws IOException если не удалось создать папку или записать файл
     */
    public static void export(List<BenchmarkResult> results, String baseFileName) throws IOException {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        File dir = new File("benchmarks");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                throw new IOException("НЕ УДАЛОСЬ СОЗДАТЬ ПАПКУ: " + dir.getAbsolutePath());
            }
        }

        File csvFile = new File(dir, baseFileName + "_" + timestamp + ".csv");
        System.out.println(">>> Попытка сохранить CSV в: " + csvFile.getAbsolutePath());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            writer.write(HEADER);
            writer.write("\n");

            for (BenchmarkResult res : results) {
                writer.write(res.toCsvRow());
                writer.write("\n");
            }
        }
        System.out.println(">>> УСПЕШНО СОХРАНЕНО: " + csvFile.getName());
    }
}