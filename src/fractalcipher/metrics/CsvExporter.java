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
 * Папка создаётся автоматически при первом вызове, если её нет
 * (Git её не хранит — это артефакт запуска, а не часть исходников).
 * Имя файла содержит отметку времени, поэтому повторные запуски не
 * перезаписывают предыдущие результаты.
 * </p>
 * <p>
 * <b>Формат CSV</b> (порядок колонок — контракт между {@link #export}
 * и {@link BenchmarkResult#toCsvRow()}):
 * </p>
 * <pre>
 * ImageName,BlackAndWhite,Permutation,Diffusion,TimeMs,
 * Correlation,Entropy,NPCR,UACI,MSE
 * </pre>
 * <p>
 * Разделитель — запятая, дробные числа — с точкой ({@code Locale.US}),
 * кодировка — системная по умолчанию (для Excel на русской локали
 * UTF-8 без BOM обычно читается как ANSI, но имена алгоритмов и
 * изображений в проекте латиницей — так что проблем нет).
 * </p>
 *
 * @author FractalCipher Team
 * @see BenchmarkResult
 */
public class CsvExporter {

    /** Утилитарный класс — инстанцирование не нужно. */
    private CsvExporter() {}

    /**
     * Заголовок CSV. <b>Должен совпадать по количеству и порядку колонок
     * с</b> {@link BenchmarkResult#toCsvRow()}, иначе данные «съедут»
     * относительно подписей.
     */
    private static final String HEADER =
            "ImageName,BlackAndWhite,Permutation,Diffusion,TimeMs,Correlation,Entropy,NPCR,UACI,MSE";


    /**
     * Сохраняет список результатов в новый CSV-файл.
     * <p>
     * Имя файла: {@code <baseFileName>_<timestamp>.csv}, где
     * {@code timestamp} — {@code yyyy-MM-dd_HH-mm-ss}. Папка
     * {@code benchmarks/} создаётся, если её нет.
     * </p>
     *
     * @param results      список результатов (не {@code null})
     * @param baseFileName базовое имя без расширения (например,
     *                     {@code "benchmark_dataset"} или
     *                     {@code "benchmark_single_image"})
     * @throws IOException если не удалось создать папку или записать файл
     *                     (нет прав, диск заполнен и т.п.)
     */
    public static void export(List<BenchmarkResult> results, String baseFileName) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Создаем папку "benchmarks" в рабочей директории
        File dir = new File("benchmarks");

        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                // Если папку создать не удалось (например, нет прав доступа)
                throw new IOException("НЕ УДАЛОСЬ СОЗДАТЬ ПАПКУ: " + dir.getAbsolutePath());
            }
        }

        File csvFile = new File(dir, baseFileName + "_" + timestamp + ".csv");

        // Печатаем ПОЛНЫЙ путь, куда сейчас будет сохранен файл
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