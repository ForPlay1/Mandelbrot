package fractalcipher.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

/**
 * Хранилище пользовательских настроек папок по умолчанию (CSV, Keys, Logs).
 * <p>
 * <b>Почему используется {@link Preferences}.</b> Настройки хранятся в
 * системном реестре / пользовательском хранилище ОС и автоматически
 * переживают перезапуск приложения — не нужно парсить конфиг-файлы
 * вручную и следить за их расположением рядом с {@code .jar}.
 * </p>
 * <p>
 * <b>Соглашение о значениях по умолчанию:</b>
 * </p>
 * <ul>
 *   <li><b>CSV</b> — если не задано, используется {@code benchmarks/}
 *       относительно рабочей директории (как и раньше);</li>
 *   <li><b>Keys</b> — если не задано ({@code null}), ключи сохраняются
 *       и ищутся рядом с зашифрованным изображением;</li>
 *   <li><b>Logs</b> — если не задано, используется {@code logs/}
 *       относительно рабочей директории.</li>
 * </ul>
 *
 * @author FractalCipher Team
 */
public final class AppSettings {

    /** Узел настроек текущего пользователя для пакета {@code fractalcipher.config}. */
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppSettings.class);

    private static final String KEY_CSV_DIR  = "default_csv_dir";
    private static final String KEY_KEYS_DIR = "default_keys_dir";
    private static final String KEY_LOGS_DIR = "default_logs_dir";

    /** Утилитарный класс — инстанцирование не нужно. */
    private AppSettings() {}

    /**
     * Возвращает папку по умолчанию для сохранения CSV-отчётов бенчмарка.
     *
     * @return заданный пользователем путь или {@code Path.of("benchmarks")},
     *         если настройка не задана
     */
    public static Path getCsvDir() {
        String val = PREFS.get(KEY_CSV_DIR, "");
        return val.isBlank() ? Paths.get("benchmarks") : Paths.get(val);
    }

    /**
     * Сохраняет папку по умолчанию для CSV-отчётов.
     *
     * @param path путь к директории (или {@code null}, чтобы сбросить
     *             на стандартную {@code benchmarks/})
     */
    public static void setCsvDir(Path path) {
        if (path == null) {
            PREFS.remove(KEY_CSV_DIR);
        } else {
            PREFS.put(KEY_CSV_DIR, path.toAbsolutePath().toString());
        }
    }

    /**
     * Возвращает папку по умолчанию для сохранения и поиска ключей ({@code .key}).
     *
     * @return заданный пользователем путь или {@code null}, если ключи
     *         должны храниться рядом с зашифрованными изображениями
     */
    public static Path getKeysDir() {
        String val = PREFS.get(KEY_KEYS_DIR, "");
        return val.isBlank() ? null : Paths.get(val);
    }

    /**
     * Сохраняет папку по умолчанию для файлов ключей ({@code .key}).
     *
     * @param path путь к директории (или {@code null}, чтобы сохранять ключи
     *             рядом с изображениями)
     */
    public static void setKeysDir(Path path) {
        if (path == null) {
            PREFS.remove(KEY_KEYS_DIR);
        } else {
            PREFS.put(KEY_KEYS_DIR, path.toAbsolutePath().toString());
        }
    }

    /**
     * Возвращает папку по умолчанию для записи логов приложения.
     *
     * @return заданный пользователем путь или {@code Path.of("logs")},
     *         если настройка не задана
     */
    public static Path getLogsDir() {
        String val = PREFS.get(KEY_LOGS_DIR, "");
        return val.isBlank() ? Paths.get("logs") : Paths.get(val);
    }

    /**
     * Сохраняет папку по умолчанию для записи логов.
     *
     * @param path путь к директории (или {@code null}, чтобы сбросить
     *             на стандартную {@code logs/})
     */
    public static void setLogsDir(Path path) {
        if (path == null) {
            PREFS.remove(KEY_LOGS_DIR);
        } else {
            PREFS.put(KEY_LOGS_DIR, path.toAbsolutePath().toString());
        }
    }

    /**
     * Создаёт директорию (включая родительские), если она ещё не существует.
     *
     * @param path путь к директории
     * @return объект {@link File} для созданной или существующей директории
     */
    public static File ensureDir(Path path) {
        File dir = path.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
