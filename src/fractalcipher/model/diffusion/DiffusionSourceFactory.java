package fractalcipher.model.diffusion;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DiffusionSourceFactory {

    private final Map<String, DiffusionSource> sources = new HashMap<>();

    public DiffusionSourceFactory() {
        register(new LogisticMapDiffusion());
        register(new HenonMapDiffusion());
        // сюда добавляются Лоренц, тент-карта и т.д. по мере реализации
    }

    public void register(DiffusionSource source) {
        sources.put(source.getName(), source);
    }

    public DiffusionSource get(String name) {
        DiffusionSource source = sources.get(name);
        if (source == null) {
            throw new IllegalArgumentException("Неизвестный источник диффузии: " + name);
        }
        return source;
    }

    public Set<String> availableNames() {
        return sources.keySet();
    }
}