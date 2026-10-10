package fractalcipher.controller;

import fractalcipher.domain.Cipher;
import fractalcipher.domain.Image;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.metrics.BenchmarkRunner;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.util.List;

public class CipherController {

    public Image handleEncrypt(Cipher cipher, Image input) {
        cipher.setImageInput(input);
        cipher.encrypt();
        return cipher.getImageOutput();
    }

    public Image handleDecrypt(Cipher cipher, Image input) {
        cipher.setImageInput(input);
        cipher.decrypt();
        return cipher.getImageOutput();
    }

    // === СЕРИЯ 1: ТОЛЬКО ПЕРЕСТАНОВКА ===
    // Перебираем все фракталы с null-диффузией.
    // CBC выключен. Показывает качество фрактала (корреляция).
    public List<BenchmarkResult> handleBenchmarkPermutationOnly(
            PermutationSourceFactory permutationFactory,
            Image image) {
        return BenchmarkRunner.runPermutationOnly(permutationFactory, image);
    }

    // === СЕРИЯ 2: ТОЛЬКО ДИФФУЗИЯ ===
    // Перебираем все хаотические карты с identity-перестановкой.
    // CBC выключен. Показывает качество диффузии (NPCR/UACI).
    public List<BenchmarkResult> handleBenchmarkDiffusionOnly(
            DiffusionSourceFactory diffusionFactory,
            Image image) {
        return BenchmarkRunner.runDiffusionOnly(diffusionFactory, image);
    }

    // === СЕРИЯ 3: ПОЛНАЯ СХЕМА ===
    // Перебираем все комбинации фрактал × диффузия.
    // CBC включён. Показывает итоговую криптостойкость и скорость.
    public List<BenchmarkResult> handleBenchmarkFull(
            PermutationSourceFactory permutationFactory,
            DiffusionSourceFactory diffusionFactory,
            Image image) {
        return BenchmarkRunner.runFull(permutationFactory, diffusionFactory, image);
    }
}