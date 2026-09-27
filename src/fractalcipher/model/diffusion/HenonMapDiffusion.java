package fractalcipher.model.diffusion;

import java.util.Map;

// Заглушка. Карта Хенона — двумерная (в отличие от одномерной
// логистической): x_{n+1} = 1 - a*x_n^2 + y_n,  y_{n+1} = b*x_n.
// Проблема, которую ещё не решили: на каждом шаге получаются ДВЕ
// величины (x и y), а нам нужен один байт — нужно выбрать, как их
// свести в одно число (взять только x? чередовать x и y? как-то
// комбинировать?). TODO для ЛР.
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