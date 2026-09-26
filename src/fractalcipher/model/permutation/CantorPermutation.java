package fractalcipher.model.permutation;

import java.util.Map;

/**
 * Заглушка. Кандидат-фаворит из вашего обсуждения с DeepSeek:
 * канторова диагональная матрица + хаотические управляющие
 * последовательности для строк/столбцов (ordered rotation
 * scrambling). Не требует квадратного изображения, однораундовая.
 * Нужно решить точную формулу диагонального перечисления. TODO.
 */
public class CantorPermutation implements PermutationSource {

    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        throw new UnsupportedOperationException("Перестановка на основе Кантора ещё не реализована");
    }

    @Override
    public String getName() {
        return "cantor";
    }
}