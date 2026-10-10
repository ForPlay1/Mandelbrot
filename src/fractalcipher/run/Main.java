package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.domain.FractalChaosCipher;
import fractalcipher.domain.Image;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        PermutationSourceFactory permutationFactory = new PermutationSourceFactory();
        DiffusionSourceFactory diffusionFactory = new DiffusionSourceFactory();

        CipherController controller = new CipherController();
        ImageView view = new ConsoleView();

        Image image = view.requestInputImage();
        if (image == null) return;

        String mode = view.requestMode();

        // === БЕНЧМАРК: три серии ===
        if ("benchmark".equalsIgnoreCase(mode)) {

            // --- СЕРИЯ 1: только перестановка ---
            System.out.println("\n=== СЕРИЯ 1: ТОЛЬКО ПЕРЕСТАНОВКА ===");
            System.out.println("Сравнение фракталов по разрушению пространственных связей.");
            System.out.println("NPCR/UACI = 0 (значения пикселей не меняются),");
            System.out.println("смотрите на корреляцию — чем ближе к 0, тем лучше.\n");
            List<BenchmarkResult> permOnly =
                    controller.handleBenchmarkPermutationOnly(permutationFactory, image);
            view.showBenchmarkResults(permOnly);

            // --- СЕРИЯ 2: только диффузия ---
            System.out.println("\n=== СЕРИЯ 2: ТОЛЬКО ДИФФУЗИЯ ===");
            System.out.println("Сравнение хаотических карт по NPCR/UACI/энтропии.");
            System.out.println("Перестановка = identity, CBC выключен.\n");
            List<BenchmarkResult> diffOnly =
                    controller.handleBenchmarkDiffusionOnly(diffusionFactory, image);
            view.showBenchmarkResults(diffOnly);

            // --- СЕРИЯ 3: полная схема ---
            System.out.println("\n=== СЕРИЯ 3: ПОЛНАЯ СХЕМА ===");
            System.out.println("Итоговая криптостойкость + скорость.");
            System.out.println("CBC с S-боксами включён — метрики определяются им.\n");
            List<BenchmarkResult> full =
                    controller.handleBenchmarkFull(permutationFactory, diffusionFactory, image);
            view.showBenchmarkResults(full);

            return;
        }

        // === ОДИНОЧНОЕ ШИФРОВАНИЕ / РАСШИФРОВКА ===
        String permutationName = view.requestPermutationName();
        String diffusionName = view.requestDiffusionName();

        FractalChaosCipher cipher = new FractalChaosCipher(
                "c1", permutationName + "+" + diffusionName,
                permutationFactory.get(permutationName),
                diffusionFactory.get(diffusionName)
        );

        try {
            Image result = "decrypt".equalsIgnoreCase(mode)
                    ? controller.handleDecrypt(cipher, image)
                    : controller.handleEncrypt(cipher, image);
            view.showResult(result);
        } catch (UnsupportedOperationException e) {
            view.showError(e.getMessage());
        }
    }
}