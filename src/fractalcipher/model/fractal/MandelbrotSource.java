package fractalcipher.model.fractal;

import fractalcipher.model.ChaosSource;
import java.util.Map;

/**
 * Заглушка. Идея (как в вашем предыдущем прототипе для текста):
 * escape-time точки (c, z0) множества Мандельброта как источник
 * псевдослучайности. Не решено, как именно проходить length точек
 * (по сетке? по траектории одной точки?) — TODO на ЛР.
 */
public class MandelbrotSource implements ChaosSource {

    @Override
    public double[] generate(int length, Map<String, Double> params) {
        // TODO: связать escape-time с последовательностью нужной длины
        throw new UnsupportedOperationException("Источник Мандельброта ещё не реализован");
    }

    @Override
    public String getName() {
        return "mandelbrot";
    }
}