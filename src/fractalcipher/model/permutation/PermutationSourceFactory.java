package fractalcipher.model.permutation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Реестр всех источников перестановки. Смысл существования этого
// класса: Controller и View обращаются только к нему по имени
// ("mandelbrot", "julia", ...), и вообще не импортируют конкретные
// классы фракталов. Чтобы добавить новый фрактал, достаточно
// реализовать PermutationSource и зарегистрировать его в конструкторе
// ниже — больше нигде в проекте ничего менять не нужно.
public class PermutationSourceFactory {

    // Ключ — имя источника (source.getName()), значение — сам объект.
    private final Map<String, PermutationSource> sources = new HashMap<>();

    public PermutationSourceFactory() {
        // Регистрируем все известные на сегодня источники.
        register(new MandelbrotPermutation());
        register(new JuliaPermutation());
        register(new CantorPermutation());
        register(new BurningShipPermutation());
        register(new CantorPermutation());
        // сюда добавляются остальные ~10-15 кандидатов по мере реализации
        // (Hilbert Curve, Sierpinski, IFS и т.д.)
        //класс отключающий перестановку
        register(new IdentityPermutation());
    }

    // === НОВОЕ: очистка реестра ===
    // Нужна для создания "фабрики только с identity" в бенчмарке.
    public void clear() {
        sources.clear();
    }

    public void register(PermutationSource source) {
        sources.put(source.getName(), source);
    }

    // Достаём источник по имени. Кидаем понятную ошибку, если имя
    // не зарегистрировано — так опечатка в EncryptionKey.permutationSourceName
    // не превратится в NullPointerException где-то дальше по коду.
    public PermutationSource get(String name) {
        PermutationSource source = sources.get(name);
        if (source == null) {
            throw new IllegalArgumentException("Неизвестный источник перестановки: " + name);
        }
        return source;
    }

    // Список всех зарегистрированных имён — нужен, например,
    // BenchmarkRunner'у, чтобы перебрать все варианты автоматически,
    // не перечисляя их вручную в Main.
    public Set<String> availableNames() {
        return sources.keySet();
    }
}