package fractalcipher.model.permutation;

import java.util.Map;

/**
 * Источник перестановки (confusion) — на основе фрактала.
 * Контракт: по параметрам вернуть массив длины length, где
 * permutation[newPosition] = originalIndex. Именно так, по
 * обсуждению с DeepSeek: генерируем double[] нужной длины,
 * сортируем, индексы после сортировки и есть правило перестановки.
 */
public interface PermutationSource {
    int[] generatePermutation(int length, Map<String, Double> params);
    String getName();
}