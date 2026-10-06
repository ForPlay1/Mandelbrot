package fractalcipher.model.diffusion;

import java.util.Map;

// Контракт для любого источника диффузии (второй этап шифра, после
// перестановки). В отличие от перестановки, диффузия не переставляет
// пиксели местами, а меняет сами их значения — тут не нужна сортировка,
// нужен просто поток псевдослучайных байт для XOR.
public interface DiffusionSource {

    // length — сколько байт нужно (в FractalChaosCipher.process() это
    // количество_пикселей * 3, по одному байту на R, G, B канал)
    // params — параметры конкретной карты (например "r" и "x0" для
    // логистической карты)
    byte[] generateStream(int length, Map<String, Double> params);

    String getName();
}