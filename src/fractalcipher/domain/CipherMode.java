package fractalcipher.domain;

public enum CipherMode {
    ENCRYPT("Шифрование"),
    DECRYPT("Дешифровка"),
    BENCHMARK("Бенчмарк"),
    BENCRYPT("Шифрование папки"),
    BENDECRYPT("Расшифровка папки"); // <-- ДОБАВЛЕНО

    private final String name;

    CipherMode(String translation) {
        this.name = translation;
    }

    public String getName() {
        return name;
    }

//    // Словарь: ключ -> функция-обработчик
//    private static final Map<CipherMode, Consumer<CipherMode>> HANDLERS;
//
//    static {
//        EnumMap<CipherMode, Consumer<CipherMode>> map = new EnumMap<>(CipherMode.class);
//        for (CipherMode m : values()) {
//            map.put(m, CipherHandlers::onMode);
//        }
//        HANDLERS = Collections.unmodifiableMap(map);
//    }
//
//    public static Map<CipherMode, Consumer<CipherMode>> handlers() {
//        return HANDLERS;
//    }
}