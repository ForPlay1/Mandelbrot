package fractalcipher.domain;

/**
 * Абстрактный шифратор изображений.
 * <p>
 * Реализует классический паттерн <b>Template Method</b>: публичный метод
 * {@link #encrypt()} задаёт НЕИЗМЕННЫЙ порядок шагов
 * ({@code preprocess → process → postprocess}), а конкретный наследник
 * решает, ЧТО происходит на каждом шаге.
 * </p>
 * <p>
 * Класс ничего не знает ни про фракталы, ни про хаотические карты — только
 * про то, что шаги должны идти в этом порядке. Это позволяет добавлять
 * новые шифры, не меняя точку входа {@link #encrypt()}.
 * </p>
 * <p>
 * Объект {@code Cipher} переиспользуем: один экземпляр можно применять
 * к разным изображениям, переустанавливая {@link #imageInput} перед
 * очередным вызовом {@link #encrypt()} или {@link #decrypt()}.
 * </p>
 *
 * @author FractalCipher Team
 * @see FractalChaosCipher
 */
public abstract class Cipher {

    /** Уникальный идентификатор шифратора (используется в логах и метаданных). */
    protected final String id;
    /** Читаемое имя шифратора (например, {@code "mandelbrot+logistic"}). */
    protected final String name;

    /**
     * Входное изображение. Не {@code final}, так как объект переиспользуется
     * между вызовами {@link #encrypt()}/{@link #decrypt()}.
     */
    protected Image imageInput;
    /**
     * Выходное изображение — результат последней операции.
     * Заполняется в {@link #postprocess()} (для шифрования) или в
     * {@link #decrypt()} (для расшифровки).
     */
    protected Image imageOutput;

    /**
     * Создаёт новый шифратор.
     *
     * @param id   уникальный идентификатор (не {@code null})
     * @param name человекочитаемое имя (не {@code null})
     */
    protected Cipher(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Устанавливает входное изображение для следующей операции.
     *
     * @param imageInput изображение для шифрования/расшифровки
     */
    public void setImageInput(Image imageInput) {
        this.imageInput = imageInput;
    }

    /**
     * Возвращает результат последней операции.
     *
     * @return выходное изображение или {@code null}, если операция ещё не выполнялась
     */
    public Image getImageOutput() {
        return imageOutput;
    }

    /**
     * @return уникальный идентификатор шифратора
     */
    public String getId() { return id; }
    /**
     * @return Читаемое имя шифратора
     */
    public String getName() { return name; }

    /**
     * Шаблонный метод шифрования.
     * <p>
     * Помечен {@code final} — наследники <b>не могут</b> изменить порядок
     * шагов или вставить что-то между ними. Это гарантирует, что
     * {@code preprocess} всегда выполнится до {@code process}, а {@code process}
     * — до {@code postprocess} в любом наследнике без исключений.
     * </p>
     * <p>
     * После успешного завершения результат доступен через {@link #getImageOutput()}.
     * </p>
     */
    public final void encrypt() {
        preprocess();
        process();
        postprocess();
    }

    /**
     * Расшифровка изображения.
     * <p>
     * В отличие от {@link #encrypt()}, не имеет навязанной трёхшаговой
     * структуры: каждый конкретный шифратор реализует её так, как удобно
     * (обычно — «снять» шаги шифрования в обратном порядке).
     * </p>
     * <p>
     * После успешного завершения результат доступен через {@link #getImageOutput()}.
     * </p>
     */
    public abstract void decrypt();

    /**
     * Шаг 1 шифрования: подготовка данных.
     * <p>
     * Обычно включает чтение пикселей из {@link #imageInput} и генерацию
     * вспомогательных структур (например, перестановки).
     * </p>
     */
    protected abstract void preprocess();
    /**
     * Шаг 2 шифрования: основная криптографическая обработка.
     * <p>
     * Здесь выполняется перестановка, диффузия и другие преобразования.
     * </p>
     */
    protected abstract void process();
    /**
     * Шаг 3 шифрования: сборка результата.
     * <p>
     * Обычно создаёт новый объект {@link Image} из обработанных пикселей
     * и сохраняет его в {@link #imageOutput}.
     * </p>
     */
    protected abstract void postprocess();
}