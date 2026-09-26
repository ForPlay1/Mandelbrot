package fractalcipher.model.diffusion;

import java.util.Map;

/**
 * Реализована по-настоящему. x_{n+1} = r*x_n*(1-x_n), значение
 * x в (0,1) отображается в байт (0..255).
 */
public class LogisticMapDiffusion implements DiffusionSource {

    @Override
    public byte[] generateStream(int length, Map<String, Double> params) {
        double r = params.getOrDefault("r", 3.99);
        double x = params.getOrDefault("x0", 0.5);

        byte[] stream = new byte[length];
        for (int i = 0; i < length; i++) {
            x = r * x * (1 - x);
            stream[i] = (byte) (((int) (x * 256)) & 0xFF);
        }
        return stream;
    }

    @Override
    public String getName() {
        return "logistic";
    }
}