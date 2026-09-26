package fractalcipher.model;

import fractalcipher.model.chaotic.HenonMapSource;
import fractalcipher.model.chaotic.LogisticMapSource;
import fractalcipher.model.fractal.JuliaSource;
import fractalcipher.model.fractal.MandelbrotSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Реестр всех источников. Именно этот класс делает архитектуру
 * расширяемой: чтобы добавить новый фрактал или карту, достаточно
 * реализовать ChaosSource и зарегистрировать её здесь — Controller
 * и View про конкретные алгоритмы ничего не знают.
 */
public class ChaosSourceFactory {

    private final Map<String, ChaosSource> sources = new HashMap<>();

    public ChaosSourceFactory() {
        register(new MandelbrotSource());
        register(new JuliaSource());
        register(new LogisticMapSource());
        register(new HenonMapSource());
    }

    public void register(ChaosSource source) {
        sources.put(source.getName(), source);
    }

    public ChaosSource get(String name) {
        ChaosSource source = sources.get(name);
        if (source == null) {
            throw new IllegalArgumentException("Неизвестный источник: " + name);
        }
        return source;
    }
}