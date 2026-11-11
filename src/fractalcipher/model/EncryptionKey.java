package fractalcipher.model;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Ключ шифрования изображения.
 * <p>
 * Хранит только мастер-сид и названия выбранных алгоритмов.
 * Все остальные параметры (начальные значения, S-боксы и т.д.)
 * будут заново детерминированно вычислены из {@code masterSeed}
 * с помощью класса {@link KeyDerivation}.
 * </p>
 * <p>
 * Реализует интерфейс {@link Serializable}, чтобы ключ можно было
 * сохранить в бинарный файл (расширение {@code .key}) рядом с
 * зашифрованным изображением.
 * </p>
 */
public class EncryptionKey implements Serializable {
    /**
     * Идентификатор версии класса для процесса сериализации.
     * <p>
     * Если структура класса изменится (например, добавятся новые поля),
     * это число нужно увеличить. Иначе при попытке загрузить старый
     * ключ возникнет исключение {@link java.io.InvalidClassException}.
     * </p>
     */
    // Версия увеличена, так как структура класса изменилась (long -> byte[])
    private static final long serialVersionUID = 2L;

    /**
     * Мастер-сид (главное зерно) для генерации всех параметров шифрования.
     * Случайный массив 256 байт.
     */
    public final byte[] masterKey;
    /**
     * Имя источника перестановки (например, "mandelbrot" или "julia").
     * Должно совпадать с результатом {@code getName()} в классе перестановки.
     */
    public final String permutationSourceName;
    /**
     * Имя источника диффузии (например, "logistic" или "henon").
     * Должно совпадать с результатом {@code getName()} в классе диффузии.
     */
    public final String diffusionSourceName;

    /**
     * Создает новый ключ шифрования.
     *
     * @param masterKey            Уникальное зерно для генерации всех параметров.
     * @param permutationSourceName Имя выбранного фрактала для перестановки.
     * @param diffusionSourceName   Имя выбранной хаотической карты для диффузии.
     */
    public EncryptionKey(byte[] masterKey, String permutationSourceName, String diffusionSourceName) {
        this.masterKey = masterKey;
        this.permutationSourceName = permutationSourceName;
        this.diffusionSourceName = diffusionSourceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncryptionKey that = (EncryptionKey) o;
        return Arrays.equals(masterKey, that.masterKey) &&
                permutationSourceName.equals(that.permutationSourceName) &&
                diffusionSourceName.equals(that.diffusionSourceName);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(masterKey);
        result = 31 * result + permutationSourceName.hashCode();
        result = 31 * result + diffusionSourceName.hashCode();
        return result;
    }
}