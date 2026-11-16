package fractalcipher.model.diffusion;

import fractalcipher.config.DiffusionMode;

import java.util.Map;

/**
 * Контракт источника диффузии — второго этапа шифра после перестановки.
 * <p>
 * В отличие от перестановки, диффузия не переставляет пиксели местами,
 * а меняет их <b>значения</b>. Поэтому здесь не нужна сортировка или
 * биекция индексов: достаточно вернуть поток псевдослучайных байт
 * длиной {@code length}. Как именно этот поток применяется к пикселям
 * (простой XOR или CBC с S-боксами) — решает
 * {@link fractalcipher.domain.FractalChaosCipher} в зависимости от
 * {@link fractalcipher.domain.EncryptionMode}.
 * </p>
 * <p>
 * Все реализации регистрируются в {@link DiffusionSourceFactory} под
 * строковым именем ({@link #getName()}), которое должно совпадать с
 * {@link DiffusionMode#getName()}, — иначе фабрика не найдёт источник.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSourceFactory
 * @see DiffusionMode
 */
public interface DiffusionSource {

    /**
     * Генерирует поток псевдослучайных байт заданной длины.
     * <p>
     * В {@link fractalcipher.domain.FractalChaosCipher} длина потока
     * равна {@code (количество пикселей) × 8}: 4 байта на ARGB-пиксель
     * (по одному на канал A, R, G, B), умноженные на 2 — по числу
     * проходов CBC в режиме
     * {@link fractalcipher.domain.EncryptionMode#FULL}. В режиме
     * {@code DIFFUSION_ONLY} запрашивается тот же объём, но используется
     * только первая половина (второй проход CBC не выполняется).
     * В {@code PERMUTATION_ONLY} источник диффузии вообще не вызывается —
     * поток заменяется массивом нулей.
     * </p>
     *
     * @param length требуемое число байт ({@code 8 × pixels.length})
     * @param params параметры конкретной карты. Обычно содержат
     *               {@code "seed"}, {@code "x0"} (для многомерных карт —
     *               также {@code "y0"}, {@code "z0"}) и {@code "sboxSeed"};
     *               для систем ОДУ дополнительно {@code "sigma"},
     *               {@code "rho"}, {@code "beta"} и т.п. В реальном
     *               шифровании все параметры выводятся из мастер-ключа
     *               через доменную сепарацию
     *               ({@link fractalcipher.model.KeyDerivation}).
     * @return массив байт длиной {@code length}
     * @throws fractalcipher.exception.InvalidConfigurationException
     *         если параметры несовместимы с требованиями карты
     *         (например, {@code mu} тент-карты вне {@code (1; 2)})
     * @throws fractalcipher.exception.EncryptionFailedException
     *         если траектория карты разошлась или стартовую точку блока
     *         не удалось подобрать
     */
    byte[] generateStream(final int length, final Map<String, Double> params);

    /**
     * @return уникальное строковое имя для регистрации в
     *         {@link DiffusionSourceFactory} и выбора в UI
     *         (например, {@code "logistic"}, {@code "henon"}, {@code "null"})
     */
    String getName();
}