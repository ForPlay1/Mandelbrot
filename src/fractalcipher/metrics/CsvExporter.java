package fractalcipher.metrics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CsvExporter {

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
            writer.write("ImageName,Permutation,Diffusion,TimeMs,Correlation,Entropy,NPCR,UACI,MSE\n");

            for (BenchmarkResult res : results) {
                writer.write(res.toCsvRow());
                writer.write("\n");
            }
        }
        System.out.println(">>> УСПЕШНО СОХРАНЕНО: " + csvFile.getName());
    }
}