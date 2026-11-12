package fractalcipher.model.diffusion;

import java.util.Map;

/**
 * Контракт источника диффузии — второго этапа шифра после перестановки.
 * <p>
 * В отличие от перестановки, диффузия не переставляет пиксели местами,
 * а меняет их <b>значения</b>. Поэтому здесь не нужна сортировка или
 * биекция индексов: достаточно вернуть поток псевдослучайных байт
 * длиной {@code length}, который потом XOR-ится с каналами пикселей
 * (или используется в CBC — зависит от {@link fractalcipher.domain.EncryptionMode}).
 * </p>
 * <p>
 * Все реализации регистрируются в {@link DiffusionSourceFactory} под
 * строковым именем ({@link #getName()}), совпадающим с
 * {@link fractalcipher.domain.DiffusionMode#getName()}.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSourceFactory
 * @see fractalcipher.domain.DiffusionMode
 */
public interface DiffusionSource {

    /**
     * Генерирует поток псевдослучайных байт заданной длины.
            * <p>
     * Длина в {@link fractalcipher.domain.FractalChaosCipher#process()}
     * равна {@code количество_пикселей × 8}: по 4 байта на ARGB-пиксель,
            * умноженные на 2 (два прохода CBC в режиме
     * {@link fractalcipher.domain.EncryptionMode#FULL}).
            * </p>
            *
            * @param length требуемое число байт
     * @param params параметры конкретной карты — обычно {@code "seed"},
            *               {@code "x0"}, {@code "sboxSeed"} и параметры самой карты
     *               ({@code "r"}, {@code "a"}, {@code "b"}, {@code "mu"} и т.п.).
            *               В реальном шифровании все параметры выводятся из мастер-ключа.
     * @return массив байт длиной {@code length}
     * @throws fractalcipher.exception.InvalidConfigurationException если параметры
     *         несовместимы с требованиями карты (например, {@code mu} вне {@code (1; 2)})
            * @throws fractalcipher.exception.EncryptionFailedException если траектория
     *         карты разошлась или стартовую точку блока не удалось подобрать
     */
    byte[] generateStream(final int length, final Map<String, Double> params);

    /**
     * @return уникальное строковое имя для регистрации в
     *         {@link DiffusionSourceFactory} и выбора в UI
     *         (например, {@code "logistic"}, {@code "henon"}, {@code "null"})
     */
    String getName();
}