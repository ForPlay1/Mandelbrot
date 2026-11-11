package fractalcipher.model.permutation;

import java.util.Map;

/**
 * Тождественная перестановка (identity).
 * <p>
 * Используется в исследовательских целях (в бенчмарке) для оценки вклада
 * диффузии отдельно от перестановки. Не меняет порядок пикселей.
 * </p>
 */
public class IdentityPermutation implements PermutationSource {
    /**
     * Возвращает массив индексов, где каждый элемент стоит на своем месте.
     *
     * @param length Количество пикселей.
     * @param params Параметры (игнорируются).
     * @return Массив {@code [0, 1, 2, ..., length-1]}.
     */
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int[] perm = new int[length];
        for (int i = 0; i < length; i++) perm[i] = i;
        return perm;
    }

    /**
     * @return Строка "identity".
     */
    @Override
    public String getName() { return "identity"; }
}