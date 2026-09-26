package fractalcipher.model.fractal;

import fractalcipher.model.ChaosSource;
import java.util.Map;

/**
 * Заглушка. Множество Жюлиа: та же итерация z = z^2 + c, что и у
 * Мандельброта, но c фиксирован как параметр карты, а по множеству
 * "бегает" z0 (стартовая точка) — то есть роли c и z0 из
 * MandelbrotSource здесь меняются местами. TODO на ЛР.
 */
public class JuliaSource implements ChaosSource {

    @Override
    public double[] generate(int length, Map<String, Double> params) {
        // TODO: реализовать по аналогии с MandelbrotSource
        throw new UnsupportedOperationException("Источник Жюлиа ещё не реализован");
    }

    @Override
    public String getName() {
        return "julia";
    }
}