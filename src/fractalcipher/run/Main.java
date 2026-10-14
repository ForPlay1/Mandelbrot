package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.domain.CipherMode;
import fractalcipher.domain.DiffusionMode;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.domain.PermutationMode;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.view.MainView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Main extends Application
{

    private static final String ENCRYPTED_SUFFIX = "_encrypted";
    private static final List<String> IMAGE_EXTENSIONS = Image.getImageExtensions();

    @Override
    public void start(Stage stage)
    {
        MainView view = new MainView();
        Scene scene = new Scene(view.getRoot(), 1300, 750);
        stage.setTitle("Фрактальное шифрование");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args)
    {
        Thread worker = new Thread(() ->
        {
            Map<Class<?>, Object> selection;
            do {
                selection = MainView.getUserParams();
                if (selection == null) {
                    try { Thread.sleep(100); }
                    catch (InterruptedException ignored) { return; }
                }
            } while (selection == null);

            runCipherFromSelection(selection);
        });
        worker.setDaemon(true);
        worker.start();

        launch(args);
    }

    private static void runCipherFromSelection(Map<Class<?>, Object> selection)
    {
        CipherMode      mode = (CipherMode)      selection.get(CipherMode.class);
        PermutationMode perm = (PermutationMode) selection.get(PermutationMode.class);
        DiffusionMode   diff = (DiffusionMode)   selection.get(DiffusionMode.class);
        File            input    = (File)            selection.get(File.class);
        Path            out   = (Path)            selection.get(Path.class);
        File            output = out.toFile();



        if (mode== null || perm == null || diff == null
                || input == null || output == null)
        {
            System.out.println("Не все параметры выбраны — выходим.");
            return;
        }


        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();

        CipherController controller = new CipherController();
        Image image;

        switch(mode){
            // Пакетное шифрование папки
            case CipherMode.BENCRYPT ->
            {
                runBatchEncrypt(
                        mode, perm, diff, input, output,
                        controller, permutationFactory, diffusionFactory
                );
                return;
            }
            // Бенчмарк: 3 серии
            case CipherMode.BENCHMARK ->
            {
                try {
                    image = Image.loadFromFile(input.getName(), input.getName(), input);
                } catch (Exception e) {
                    System.out.println("Не удалось загрузить изображение: " + e.getMessage());
                    return;
                }
                if (image == null) return;

                System.out.println("\n=== СЕРИЯ 1: ТОЛЬКО ПЕРЕСТАНОВКА ===");
                System.out.println("Сравнение фракталов по разрушению пространственных связей.");
                System.out.println("NPCR/UACI = 0 (значения пикселей не меняются),");
                System.out.println("смотрите на корреляцию — чем ближе к 0, тем лучше.\n");
                List<BenchmarkResult> permOnly =
                        controller.handleBenchmarkPermutationOnly(permutationFactory, image);
                System.out.println(permOnly);

                System.out.println("\n=== СЕРИЯ 2: ТОЛЬКО ДИФФУЗИЯ ===");
                System.out.println("Сравнение хаотических карт по NPCR/UACI/энтропии.");
                System.out.println("Перестановка = identity, CBC выключен.\n");
                List<BenchmarkResult> diffOnly =
                        controller.handleBenchmarkDiffusionOnly(diffusionFactory, image);
                System.out.println(diffOnly);

                System.out.println("\n=== СЕРИЯ 3: ПОЛНАЯ СХЕМА ===");
                System.out.println("Итоговая криптостойкость + скорость.");
                System.out.println("CBC с S-боксами включён — метрики определяются им.\n");
                List<BenchmarkResult> full =
                        controller.handleBenchmarkFull(permutationFactory, diffusionFactory, image);
                System.out.println(full);

                return;
            }
            case CipherMode.ENCRYPT, CipherMode.DECRYPT ->
            {
                try {
                    image = Image.loadFromFile(input.getName(), input.getName(), input);
                } catch (Exception e) {
                    System.out.println("Не удалось загрузить изображение: " + e.getMessage());
                    return;
                }
                if (image == null) return;

                FractalChaosCipher cipher = new FractalChaosCipher(
                    "c1", perm.getTranslation() + "+" + diff.getTranslation(),
                    permutationFactory.get(perm.getTranslation()),
                    diffusionFactory.get(diff.getTranslation())
                );
                try{
                    Image result = (mode == CipherMode.ENCRYPT)
                            ? controller.handleEncrypt(cipher, image)
                            : controller.handleDecrypt(cipher, image);

                    File target = buildSingleResultFile(output.toPath(), input, mode.getTranslation());
                    if (result != null && result.getImage() != null) {
                        if (!ImageIO.write(result.getImage(), "png", target)) {
                            throw new IOException("не удалось записать PNG: " + target.getAbsolutePath());
                        }
                        System.out.println("Результат: " + target.getAbsolutePath());
                    }
                } catch (UnsupportedOperationException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    System.out.println("Ошибка записи результата: " + e.getMessage());
                }

            }
            default -> {}
        }
    }

    /** Имя итогового файла: <имя_входа>_encrypted.png или <имя>_decrypted.png. */
    private static File buildSingleResultFile(Path outputDir, File input, String mode) {
        String suffix = "decrypt".equalsIgnoreCase(mode) ? "_decrypted" : ENCRYPTED_SUFFIX;
        return outputDir.resolve(baseName(input.getName()) + suffix + ".png").toFile();
    }

    private static void runBatchEncrypt(CipherMode mode,
                                        PermutationMode perm,
                                        DiffusionMode diff,
                                        File input,
                                        File output,
                                        CipherController controller,
                                        PermutationSourceFactory permutationFactory,
                                        DiffusionSourceFactory diffusionFactory)
    {
        if (input == null || !input.isDirectory()) return; // тут будет обработчик ошибки
        if (output == null || !output.isDirectory()) {
            System.out.println("Папка результата недоступна"); // тут будет нормальное логирование
            return;
        }

        File[] files = input.listFiles(f -> f.isFile() && isImageToEncrypt(f.getName()));
        if (files == null || files.length == 0) {
            System.out.println("В папке нет изображений для шифрования ("
                    + String.join(" ", IMAGE_EXTENSIONS) + ")");
            return;
        }
        Arrays.sort(files);

        FractalChaosCipher cipher;
        try {
            cipher = new FractalChaosCipher(
                    "c1", perm.getTranslation() + "+" + diff.getTranslation(),
                    permutationFactory.get(perm.getTranslation()),
                    diffusionFactory.get(diff.getTranslation())
            );
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        System.out.println("Найдено файлов: " + files.length);
        System.out.println("Результат в: " + output.toString());
        int ok = 0;
        int failed = 0;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String prefix = "[" + (i + 1) + "/" + files.length + "] " + file.getName() + ": ";
            try {
                long start = System.currentTimeMillis();

                Image inputImage = Image.loadFromFile(file.getName(), file.getName(), file);
                Image result = controller.handleEncrypt(cipher, inputImage);

                File target = new File(output,
                        baseName(file.getName()) + ENCRYPTED_SUFFIX + ".png");
                if (!ImageIO.write(result.getImage(), "png", target)) {
                    throw new IOException("не удалось записать PNG");
                }

                System.out.println(prefix + "OK -> " + target.getName()
                        + " (" + (System.currentTimeMillis() - start) + " мс)");
                ok++;
            } catch (Exception e) {
                System.out.println(prefix + e.getMessage());
                failed++;
            }
        }

        System.out.println("Готово. Успешно: " + ok + ", с ошибкой: " + failed);
    }

    private static boolean isImageToEncrypt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext) && !baseName(fileName).endsWith(ENCRYPTED_SUFFIX);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}