package fractalcipher.exception;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Централизованная обработка ошибок.
 * Все исключения приложения проходят через этот класс:
 *  - классификация;
 *  - логирование;
 *  - безопасное сообщение для пользователя.
 */
public final class ErrorHandler {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static PrintStream logStream = System.err;
    private static boolean verbose = false;

    private ErrorHandler() {}

    public static void setVerbose(boolean v) { verbose = v; }
    public static void setLogStream(PrintStream stream) { logStream = stream; }

    /** Обработка исключения: классификация + лог + безопасное сообщение. */
    public static String handle(Throwable t) {
        ErrorCategory category = classify(t);
        log(t, category);
        return safeMessage(t, category);
    }

    public enum ErrorCategory {
        USER_INPUT("Ошибка ввода"),
        CONFIGURATION("Ошибка конфигурации"),
        ENCRYPTION("Ошибка шифрования"),
        IO("Ошибка ввода-вывода"),
        INTERNAL("Внутренняя ошибка");

        private final String displayName;
        ErrorCategory(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private static ErrorCategory classify(Throwable t) {
        if (t instanceof FractalCipherException fce) return fce.getCategory();
        if (t instanceof java.io.IOException) return ErrorCategory.IO;
        if (t instanceof IllegalArgumentException) return ErrorCategory.USER_INPUT;
        if (t instanceof UnsupportedOperationException) return ErrorCategory.CONFIGURATION;
        if (t instanceof SecurityException) return ErrorCategory.ENCRYPTION;
        return ErrorCategory.INTERNAL;
    }

    private static void log(Throwable t, ErrorCategory category) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        logStream.println("[" + timestamp + "] [" + category.name() + "] "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        if (verbose) t.printStackTrace(logStream);
    }

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