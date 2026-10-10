package fractalcipher.model.diffusion;

import java.util.Map;

// Временная заглушка
public class NullDiffusion implements DiffusionSource {
    @Override
    public byte[] generateStream(int length, Map<String, Double> params) {
        return new byte[length]; // все нули
    }
    @Override
    public String getName() { return "null"; }
}