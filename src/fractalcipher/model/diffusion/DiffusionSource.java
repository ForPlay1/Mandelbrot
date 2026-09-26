package fractalcipher.model.diffusion;

import java.util.Map;

/**
 * Источник диффузии — быстрая хаотическая карта, которая выдаёт
 * поток байт для XOR с каналами пикселей (после перестановки).
 */
public interface DiffusionSource {
    byte[] generateStream(int length, Map<String, Double> params);
    String getName();
}