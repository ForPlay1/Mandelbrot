package fractalcipher.model.chaotic;

import fractalcipher.model.ChaosSource;
import java.util.Map;

/**
 * Заглушка. Карта Хенона: x_{n+1} = 1 - a*x_n^2 + y_n, y_{n+1} = b*x_n.
 * Формула известна, но не решили, как из пары (x, y) на каждом шаге
 * получать один "пиксельный" псевдослучайный элемент — TODO на ЛР.
 */
public class HenonMapSource implements ChaosSource {

    @Override
    public double[] generate(int length, Map<String, Double> params) {
        // TODO: итерации карты Хенона + свёртка (x, y) -> одно значение
        throw new UnsupportedOperationException("Карта Хенона ещё не реализована");
    }

    @Override
    public String getName() {
        return "henon";
    }
}