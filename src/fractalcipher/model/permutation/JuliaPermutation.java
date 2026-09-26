package fractalcipher.model.permutation;

import java.util.Map;

/**
 * Заглушка. По аналогии с MandelbrotPermutation, но параметр c
 * фиксирован (часть ключа), а по изображению "бегает" z0 —
 * нужно решить, как связать (row, col) с z0. TODO на ЛР.
 */
public class JuliaPermutation implements PermutationSource {

    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        throw new UnsupportedOperationException("Перестановка на основе Жюлиа ещё не реализована");
    }

    @Override
    public String getName() {
        return "julia";
    }
}