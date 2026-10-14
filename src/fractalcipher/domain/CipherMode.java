package fractalcipher.domain;

public enum CipherMode {
    ENCRYPT("Шифрование"),
    DECRYPT("Дешифровка"),
    BENCHMARK("Бенчмарк"),
    BENCRYPT("Шифрование папки");

    private final String translation;

    CipherMode(String translation) {
        this.translation = translation;
    }

    public String getTranslation() {
        return translation;
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