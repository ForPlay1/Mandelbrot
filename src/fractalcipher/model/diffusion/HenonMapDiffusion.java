package fractalcipher.model.diffusion;

import java.util.Map;

/**
 * Заглушка. Карта Хенона: x_{n+1} = 1 - a*x_n^2 + y_n, y_{n+1} = b*x_n.
 * Не решено, как из пары (x, y) на каждом шаге получать один байт. TODO.
 */
public class HenonMapDiffusion implements DiffusionSource {

    @Override
    public byte[] generateStream(int length, Map<String, Double> params) {
        throw new UnsupportedOperationException("Диффузия на основе карты Хенона ещё не реализована");
    }

    @Override
    public String getName() {
        return "henon";
    }
}