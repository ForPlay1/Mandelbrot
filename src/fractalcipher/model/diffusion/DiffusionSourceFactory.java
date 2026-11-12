package fractalcipher.model.diffusion;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import fractalcipher.exception.UnsupportedSourceException;

/**
 * Реестр источников диффузии.
 * <p>
 * Устроен так же, как
 * {@link fractalcipher.model.permutation.PermutationSourceFactory}:
 * {@code Controller} и {@code View} обращаются сюда по имени
 * (например, {@code "logistic"}), не импортируя конкретные классы
 * карт. Чтобы добавить новую карту, достаточно реализовать
 * {@link DiffusionSource} и зарегистрировать её в конструкторе.
 * </p>
 * <p>
 * <b>Зачем нужен {@link #clear()}.</b> В {@code BenchmarkRunner} для
 * серии «только перестановка» требуется фабрика диффузии, в которой
 * зарегистрирован ровно один источник — {@code null}. Без {@code clear()}
 * пришлось бы каждый раз создавать новый подкласс фабрики.
 * </p>
 *
 * @author FractalCipher Team
 * @see DiffusionSource
 * @see fractalcipher.domain.DiffusionMode
 */
public class DiffusionSourceFactory {

    /** Внутренний реестр: строковое имя → реализация источника. */
    private final Map<String, DiffusionSource> sources = new HashMap<>();

    /**
     * Регистрирует все известные источники диффузии.
     * <p>
     * Имена должны совпадать с {@link fractalcipher.domain.DiffusionMode#getName()}.
     * </p>
     */
    public DiffusionSourceFactory() {
        register(new LogisticMapDiffusion());
        register(new HenonMapDiffusion());
        register(new LorenzDiffusion());
        register(new RosslerDiffusion());
        register(new ArnoldMapDiffusion());
        register(new TentMapDiffusion());
        register(new NullDiffusion());
    }

    /**
     * Очищает реестр. Используется в бенчмарке для создания
     * «фабрики только с {@code null}».
     */
    public void clear() {
        sources.clear();
    }

    /**
     * Регистрирует источник под именем {@link DiffusionSource#getName()}.
     * <p>
     * Повторная регистрация источника с тем же именем замещает
     * предыдущий — удобно для тестов и бенчмарка.
     * </p>
     *
     * @param source экземпляр реализации {@link DiffusionSource}
     */
    public void register(final DiffusionSource source) {
        sources.put(source.getName(), source);
    }

    /**
     * Возвращает источник по имени.
     *
     * @param name строковое имя (например, {@code "logistic"})
     * @return зарегистрированный источник
     * @throws UnsupportedSourceException если имя не найдено
     *         (опечатка или ключ от старой версии приложения)
     */
    public DiffusionSource get(final String name) {
        final DiffusionSource source = sources.get(name);
        if (source == null) {
            throw new UnsupportedSourceException("Неизвестный источник диффузии: " + name);
        }
        return source;
    }

    /**
     * @return множество всех зарегистрированных имён (нужно бенчмарку
     *         для автоматического перебора)
     */
    public Set<String> availableNames() {
        return sources.keySet();
    }
}