package fractalcipher.domain;

/**
 * Абстрактный шифратор — ровно та объектная модель, которую описал
 * преподаватель: id, название, imageInput, imageOutput, и метод
 * шифрования, который прогоняет 3 абстрактных шага.
 *
 * Это классический паттерн Template Method: encrypt() задаёт
 * НЕИЗМЕННЫЙ порядок шагов (preprocess -> process -> postprocess),
 * а каждый конкретный шифратор-наследник решает, ЧТО именно
 * происходит на каждом шаге. Сам Cipher не знает ни про фракталы,
 * ни про хаотические карты — только про то, что шаги идут в таком
 * порядке.
 */
public abstract class Cipher {

    protected final String id;
    protected final String name;

    // Не final — эти поля меняются между вызовами: один и тот же
    // объект Cipher можно переиспользовать для разных изображений,
    // просто переустановив imageInput перед следующим encrypt()/decrypt().
    protected Image imageInput;
    protected Image imageOutput;

    protected Cipher(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void setImageInput(Image imageInput) {
        this.imageInput = imageInput;
    }

    public Image getImageOutput() {
        return imageOutput;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    /**
     * Шаблонный метод. Помечен final — наследники НЕ МОГУТ поменять
     * порядок шагов или вставить что-то между ними. Это осознанное
     * ограничение: гарантирует, что preprocess всегда отработает
     * до process, а process — до postprocess, в любом наследнике,
     * без исключений.
     */
    public final void encrypt() {
        preprocess();
        process();
        postprocess();
    }

    /**
     * Расшифровка. В задании преподавателя регламентирован только
     * метод ШИФРОВАНИЯ (encrypt через 3 шага) — про decrypt речи не
     * было, поэтому он оставлен одним абстрактным методом: каждый
     * конкретный шифратор реализует его так, как считает нужным,
     * без навязанной 3-шаговой структуры.
     */
    public abstract void decrypt();

    // protected, а не public — это "внутренняя кухня" конкретного
    // шифратора, снаружи (из Controller) вызывается только encrypt()/decrypt().
    protected abstract void preprocess();
    protected abstract void process();
    protected abstract void postprocess();
}