package fractalcipher.model.permutation;

import java.util.Map;

// Заглушка. Из обсуждения с DeepSeek — кандидат-фаворит по скорости:
// канторова диагональная матрица плюс хаотические управляющие
// последовательности для вращения строк/столбцов. Плюсы: не требует
// квадратного изображения, однораундовая (быстрее, чем несколько
// проходов других схем). Минус: формула диагонального перечисления
// ещё не выбрана окончательно — TODO для ЛР.
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