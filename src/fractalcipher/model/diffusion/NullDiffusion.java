package fractalcipher.model.diffusion;

import java.util.Map;

/**
 * Заглушка источника диффузии: возвращает поток из нулей.
 * <p>
 * Используется в двух сценариях:
 * </p>
 * <ul>
 *   <li>В серии бенчмарка «только перестановка» — чтобы изолировать
 *       вклад перестановки (XOR с нулём данных не меняет).</li>
 *   <li>Как заглушка в тестах и при отладке, когда нужен «прозрачный»
 *       источник диффузии.</li>
 * </ul>
 * <p>
 * <b>Не использовать в реальном шифровании</b> — это фактически
 * отключение второго этапа.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 * @see fractalcipher.metrics.BenchmarkRunner
 */
public class NullDiffusion implements DiffusionSource {

    /**
     * Возвращает массив нулей длиной {@code length}.
     *
     * @param length желаемая длина (неотрицательная)
     * @param params игнорируются
     * @return массив нулей длиной {@code length}
     */
    @Override
    public byte[] generateStream(final int length, final Map<String, Double> params) {
        return new byte[length]; // все нули
    }

    /** @return строковое имя источника — {@code "null"} */
    @Override
    public String getName() { return "null"; }
}