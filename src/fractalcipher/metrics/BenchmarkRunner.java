package fractalcipher.metrics;

import fractalcipher.model.EncryptionKey;
import fractalcipher.model.ImageCipher;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// Инструмент сравнения кандидатов: перебирает ВСЕ пары
// (источник перестановки x источник диффузии), шифрует одно и то же
// тестовое изображение каждой парой, засекает время и считает метрики.
// Именно этот класс закрывает задачу "сравнить ~10-15 фракталов" —
// добавление нового источника в *Factory автоматически включает его
// в сравнение, без единой правки здесь.
public class BenchmarkRunner {

    public static List<BenchmarkResult> run(ImageCipher cipher, BufferedImage originalImage,
                                            List<String> permutationNames, List<String> diffusionNames) {
        List<BenchmarkResult> results = new ArrayList<>();

        // Для NPCR/UACI нужна вторая версия изображения, отличающаяся
        // от оригинала на минимум (1 бит одного пикселя) — это
        // стандартная методика проверки лавинного эффекта.
        BufferedImage modifiedImage = withOnePixelFlipped(originalImage);

        // Двойной цикл — декартово произведение всех перестановок
        // на все диффузии, то есть все возможные комбинации схемы.
        for (String permutationName : permutationNames) {
            for (String diffusionName : diffusionNames) {
                // Пустые HashMap для параметров — значит, каждый источник
                // возьмёт свои значения по умолчанию (см. getOrDefault
                // внутри каждого source). Если бы для бенчмарка были
                // нужны конкретные параметры — их передавали бы сюда.
                EncryptionKey key = new EncryptionKey(
                        permutationName, new HashMap<>(),
                        diffusionName, new HashMap<>()
                );

                try {
                    // System.currentTimeMillis() до и после — простой
                    // способ измерить время шифрования именно этой пары.
                    long start = System.currentTimeMillis();
                    BufferedImage encrypted = cipher.encrypt(originalImage, key);
                    long elapsed = System.currentTimeMillis() - start;

                    // Шифруем и слегка изменённую версию — тем же ключом,
                    // чтобы можно было сравнить два шифротекста для NPCR/UACI.
                    BufferedImage encryptedModified = cipher.encrypt(modifiedImage, key);

                    results.add(new BenchmarkResult(
                            permutationName, diffusionName, elapsed,
                            ImageMetrics.correlationCoefficient(encrypted),
                            ImageMetrics.entropy(encrypted),
                            ImageMetrics.npcr(encrypted, encryptedModified),
                            ImageMetrics.uaci(encrypted, encryptedModified)
                    ));
                } catch (UnsupportedOperationException e) {
                    // Источник ещё не реализован (заглушка кинула
                    // исключение) — не роняем весь бенчмарк, а просто
                    // пропускаем эту пару и сообщаем об этом в консоль.
                    System.out.println("[Пропущено] " + permutationName + " + " + diffusionName + ": " + e.getMessage());
                }
            }
        }
        return results;
    }

    // Делает копию изображения и меняет 1 бит ровно в одном пикселе
    // (самом первом, координата (0,0)) — минимальное возможное
    // изменение входа для проверки лавинного эффекта.
    private static BufferedImage withOnePixelFlipped(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Создаём новый BufferedImage и копируем в него все пиксели —
        // нельзя менять оригинал, он ещё понадобится "как есть".
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, width, height, image.getRGB(0, 0, width, height, null, 0, width), 0, width);

        int pixel = copy.getRGB(0, 0);
        // XOR с 0x00000001 переключает ровно 1 (самый младший) бит —
        // это минимально возможное изменение одного канала пикселя.
        copy.setRGB(0, 0, pixel ^ 0x00000001);
        return copy;
    }
}