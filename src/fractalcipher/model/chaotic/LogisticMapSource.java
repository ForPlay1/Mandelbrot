package fractalcipher.model.chaotic;

import fractalcipher.model.ChaosSource;
import java.util.Map;

/**
 * Логистическая карта x_{n+1} = r * x_n * (1 - x_n).
 * Реализована по-настоящему (не заглушка) — она достаточно
 * простая, чтобы служить рабочим примером для остальных
 * источников и для проверки, что Controller/View корректно
 * дёргают ChaosSource через общий интерфейс.
 */
public class LogisticMapSource implements ChaosSource {

    @Override
    public double[] generate(int length, Map<String, Double> params) {
        double r = params.getOrDefault("r", 3.99);
        double x = params.getOrDefault("x0", 0.5);

        double[] sequence = new double[length];
        for (int i = 0; i < length; i++) {
            x = r * x * (1 - x);
            sequence[i] = x;
        }
        return sequence;
    }

    @Override
    public String getName() {
        return "logistic";
    }
}