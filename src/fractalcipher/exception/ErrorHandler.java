package fractalcipher.exception;

import fractalcipher.utils.AppLogger;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Централизованная обработка ошибок приложения.
 * <p>
 * Через этот класс проходят все исключения перед тем, как попасть
 * к пользователю. Алгоритм один:
 * </p>
 * <ol>
 *   <li><b>Классификация</b> — {@link #classify(Throwable)} определяет
 *       {@link ErrorCategory} по типу исключения.</li>
 *   <li><b>Логирование</b> — {@link #log(Throwable, ErrorCategory)} пишет
 *       запись в {@link AppLogger} (или в {@code System.err}, если логгер
 *       не инициализирован — например, нет прав на запись в {@code logs/}).</li>
 *   <li><b>Безопасное сообщение</b> — {@link #safeMessage(Throwable, ErrorCategory)}
 *       возвращает текст для UI, скрывающий внутренние детали
 *       (стектрейсы, пути к файлам).</li>
 * </ol>
 * <p>
 * Класс нельзя инстанцировать — только статические методы.
 * </p>
 *
 * @author FractalCipher Team
 * @see FractalCipherException
 * @see AppLogger
 */
public final class ErrorHandler {

    /** Формат метки времени для запасного вывода в {@code System.err}. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Поток для запасного логирования, когда {@link AppLogger} недоступен.
     * <p>
     * <b>Внимание:</b> сейчас поле фактически не используется —
     * {@link #log(Throwable, ErrorCategory)} пишет напрямую в
     * {@code System.err}. Метод {@link #setLogStream(PrintStream)} сохранён
     * на будущее (например, для перенаправления в тестах), но при текущей
     * реализации смена потока не даст эффекта. Если нужна гибкость —
     * замените внутри {@code log(...)} вызов {@code System.err.println}
     * на {@code logStream.println}.
     * </p>
     */
    private static PrintStream logStream = System.err;

    /** Если {@code true} — при запасном логировании печатается полный стектрейс. */
    private static boolean verbose = false;

    /** Утилитарный класс — инстанцирование запрещено. */
    private ErrorHandler() {}

    /**
     * Включает или выключает печать стектрейсов при запасном логировании
     * (когда {@link AppLogger} недоступен).
     *
     * @param v {@code true} — печатать стектрейс
     */
    public static void setVerbose(boolean v) { verbose = v; }

    /**
     * Задаёт поток для запасного логирования.
     * <p>См. предупреждение у поля {@link #logStream}: при текущей
     * реализации метода {@link #log} значение не оказывает эффекта.</p>
     *
     * @param stream поток вывода (например, {@code System.out} для тестов)
     */
    public static void setLogStream(PrintStream stream) { logStream = stream; }

    /**
     * Главная точка входа: классифицирует исключение, пишет лог
     * и возвращает безопасное сообщение для пользователя.
     *
     * @param t любое исключение или ошибка (не {@code null})
     * @return текст для UI, не содержащий стектрейсов и путей
     */
    public static String handle(Throwable t) {
        ErrorCategory category = classify(t);
        log(t, category);
        return safeMessage(t, category);
    }

    /**
     * Категории ошибок. Определяют уровень логирования и «безопасный»
     * текст сообщения.
     */
    public enum ErrorCategory {
        /** Ошибка ввода пользователя — можно показать детали. */
        USER_INPUT("Ошибка ввода"),

        /** Ошибка конфигурации (ключ, режим, источник) — детали полезны в логах. */
        CONFIGURATION("Ошибка конфигурации"),

        /** Ошибка в процессе шифрования/расшифровки — пользователю показывается общий текст. */
        ENCRYPTION("Ошибка шифрования"),

        /** Ошибка ввода-вывода (чтение/запись файла). */
        IO("Ошибка ввода-вывода"),

        /** Внутренняя ошибка — детали скрываются полностью. */
        INTERNAL("Внутренняя ошибка");

        /** Название категории для отображения (например, в диалогах). */
        private final String displayName;

        ErrorCategory(String displayName) { this.displayName = displayName; }

        /** @return Читаемое название категории */
        public String getDisplayName() { return displayName; }
    }

    /**
     * Определяет категорию исключения по его типу.
     * <p>
     * Порядок проверок важен:
     * </p>
     * <ol>
     *   <li>{@link FractalCipherException} — доверяем категории, которую
     *       наследник проставил сам (самый точный источник истины);</li>
     *   <li>стандартные исключения Java — маппим по типу;</li>
     *   <li>всё прочее — {@link ErrorCategory#INTERNAL}.</li>
     * </ol>
     * <p>
     * <b>Про {@link SecurityException} → {@link ErrorCategory#ENCRYPTION}:</b>
     * строго говоря, {@code SecurityException} чаще означает «нет доступа»
     * (это ближе к {@code IO}), но в контексте этого приложения он возникает
     * при попытке прочитать/записать ключ и не должен раскрывать детали
     * пользователю — поэтому маппинг на ENCRYPTION даёт безопасное
     * сообщение «Шифрование не удалось…». Если поведение изменится,
     * проверку стоит перенести выше и завести отдельную категорию.
     * </p>
     *
     * @param t исключение
     * @return категория ошибки (никогда {@code null})
     */
    private static ErrorCategory classify(Throwable t) {
        if (t instanceof FractalCipherException fce) return fce.getCategory();
        if (t instanceof java.io.IOException) return ErrorCategory.IO;
        if (t instanceof IllegalArgumentException) return ErrorCategory.USER_INPUT;
        if (t instanceof UnsupportedOperationException) return ErrorCategory.CONFIGURATION;
        if (t instanceof SecurityException) return ErrorCategory.ENCRYPTION;
        return ErrorCategory.INTERNAL;
    }

    /**
     * Пишет запись в лог: через {@link AppLogger}, если он инициализирован,
     * иначе — напрямую в {@code System.err} с меткой времени.
     * <p>
     * Уровень выбирается по категории: {@code INTERNAL} и {@code ENCRYPTION}
     * пишутся как {@code SEVERE} (с сохранением стектрейса), остальные —
     * как {@code WARNING} (без стектрейса).
     * </p>
     *
     * @param t        исключение
     * @param category его категория (результат {@link #classify(Throwable)})
     */
    private static void log(Throwable t, ErrorCategory category) {
        String message = "[" + category.name() + "] " + t.getClass().getSimpleName() + ": " + t.getMessage();

        // Проверяем, удалось ли инициализировать AppLogger
        if (AppLogger.isInitialized()) {
            // Логируем через AppLogger
            if (category == ErrorCategory.INTERNAL || category == ErrorCategory.ENCRYPTION) {
                AppLogger.error(message, t);
            } else {
                AppLogger.warning(message);
            }
        } else {
            // Если AppLogger не инициализирован (например, нет прав на запись),
            // пишем напрямую в консоль, чтобы ошибка не потерялась.
            String timestamp = LocalDateTime.now().format(TIMESTAMP);
            System.err.println("[" + timestamp + "] " + message);
            if (verbose) t.printStackTrace(System.err);
        }
    }

    /**
     * Формирует безопасное для пользователя сообщение.
     * <p>
     * Для {@code ENCRYPTION} и {@code INTERNAL} текст не содержит деталей —
     * только общую формулировку. Для {@code USER_INPUT}, {@code CONFIGURATION}
     * и {@code IO} сообщение исключения включается в ответ (оно не содержит
     * внутренних имён и предназначено для пользователя).
     * </p>
     *
     * @param t        исключение
     * @param category его категория
     * @return текст для UI
     */
    private static String safeMessage(Throwable t, ErrorCategory category) {
        return switch (category) {
            case USER_INPUT -> "Некорректный ввод: " + t.getMessage();
            case CONFIGURATION -> "Ошибка конфигурации: " + t.getMessage();
            case ENCRYPTION -> "Шифрование не удалось. Проверьте ключ и входные данные.";
            case IO -> "Ошибка ввода-вывода: " + t.getMessage();
            case INTERNAL -> "Внутренняя ошибка. Обратитесь к администратору.";
        };
    }
}