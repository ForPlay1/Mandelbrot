package fractalcipher.model.permutation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PermutationSourceFactory {

    private final Map<String, PermutationSource> sources = new HashMap<>();

    public PermutationSourceFactory() {
        register(new MandelbrotPermutation());
        register(new JuliaPermutation());
        register(new CantorPermutation());
        // сюда добавляются остальные ~10-15 кандидатов по мере реализации
    }

    public void register(PermutationSource source) {
        sources.put(source.getName(), source);
    }

    public PermutationSource get(String name) {
        PermutationSource source = sources.get(name);
        if (source == null) {
            throw new IllegalArgumentException("Неизвестный источник перестановки: " + name);
        }
        return source;
    }

    public Set<String> availableNames() {
        return sources.keySet();
    }
}