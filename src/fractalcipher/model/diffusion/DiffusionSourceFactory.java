package fractalcipher.model.diffusion;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Реестр источников диффузии — устроен ровно так же, как
// PermutationSourceFactory, и по той же причине: Controller/View
// не должны знать про конкретные формулы карт.
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