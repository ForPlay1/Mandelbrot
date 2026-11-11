package fractalcipher.model.permutation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import fractalcipher.exception.UnsupportedSourceException;

/**
 * Реестр всех источников перестановки.
 * <p>
 * Смысл существования этого класса: Controller и View обращаются только к нему
 * по имени (например, "mandelbrot"), и не импортируют конкретные классы фракталов.
 * Чтобы добавить новый фрактал, достаточно реализовать {@link PermutationSource}
 * и зарегистрировать его в конструкторе ниже.
 * </p>
 */
public class PermutationSourceFactory {

    /**
     * Внутренний реестр: ключ — имя источника, значение — сам объект.
     */
    private final Map<String, PermutationSource> sources = new HashMap<>();

    /**
     * Конструктор регистрирует все известные на сегодня источники.
     */
    public PermutationSourceFactory() {
        register(new MandelbrotPermutation());
        register(new JuliaPermutation());
        register(new CantorPermutation());
        register(new BurningShipPermutation());
        register(new SierpinskiPermutation()); // <-- НОВОЕ
        register(new DragonPermutation());     // <-- НОВОЕ
        //класс отключающий перестановку
        register(new IdentityPermutation());
    }

    /**
     * Очищает реестр. Используется в бенчмарке для создания
     * "фабрики только с identity" или "только с null".
     */
    public void clear() {
        sources.clear();
    }

    /**
     * Регистрирует новый источник перестановки.
     *
     * @param source Экземпляр реализации {@link PermutationSource}.
     */
    public void register(PermutationSource source) {
        sources.put(source.getName(), source);
    }

    /**
     * Достает источник по имени.
     *
     * @param name Имя источника.
     * @return Найденный объект.
     * @throws UnsupportedSourceException если имя не зарегистрировано.
     *         Это защищает от опечаток в ключах шифрования.
     */
    public PermutationSource get(String name) {
        PermutationSource source = sources.get(name);
        if (source == null) {
            throw new UnsupportedSourceException("Неизвестный источник перестановки: " + name);
        }
        return source;
    }

    /**
     * Возвращает список всех зарегистрированных имен.
     * Нужен бенчмарку, чтобы перебрать все варианты автоматически.
     *
     * @return Множество уникальных имен.
     */
    public Set<String> availableNames() {
        return sources.keySet();
    }
}