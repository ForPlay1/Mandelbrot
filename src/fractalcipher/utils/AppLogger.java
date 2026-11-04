package fractalcipher.utils;

import fractalcipher.exception.ErrorHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("FractalCipher");
    private static boolean initialized = false;

    // Метод для проверки, удалось ли инициализировать логгер
    public static boolean isInitialized(){
        return initialized;
    }
    static {
        try {
            Files.createDirectories(Paths.get("logs"));

            // === 1. Основной лог с ротацией ===
            // Шаблон "app-%g.log", 5 МБ на файл, 5 файлов, дописывать
            FileHandler fileHandler = new FileHandler(
                    "logs/app-%g.log",
                    5 * 1024 * 1024,  // 5 МБ
                    5,                 // 5 файлов (rotating)
                    true               // дописывание в конец
            );
            fileHandler.setFormatter(new CustomFormatter());
            fileHandler.setLevel(Level.INFO);

            // === 2. Отдельный файл для критических ошибок ===
            FileHandler criticalHandler = new FileHandler("logs/critical.log", true);
            criticalHandler.setFormatter(new CustomFormatter());
            criticalHandler.setLevel(Level.SEVERE);

            // === 3. Консоль ===
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new CustomFormatter());
            consoleHandler.setLevel(Level.INFO);

            LOGGER.addHandler(fileHandler);
            LOGGER.addHandler(criticalHandler);
            LOGGER.addHandler(consoleHandler);
            LOGGER.setUseParentHandlers(false);

            // === 4. Shutdown Hook — гарантирует flush при выходе ===
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (Handler h : LOGGER.getHandlers()) {
                    h.flush();
                    h.close();
                }
            }));

            initialized = true;
        }
        catch (IOException e) {
            // Если не удалось создать логи, отправляем ошибку в глобальный обработчик.
            // ErrorHandler увидит, что initialized == false, и выведет ошибку в консоль.
            ErrorHandler.handle(e);
        }
    }
    public static void info(String message) {
        if (initialized) {
            LOGGER.log(Level.INFO, message);
        } else {
            // Запасной вариант, если файлы логов недоступны
            System.out.println("[INFO] " + message);
        }
    }

    public static void warning(String message) {
        if (initialized) {
            LOGGER.log(Level.WARNING, message);
        } else {
            System.out.println("[WARNING] " + message);
        }
    }

    public static void error(String message, Throwable t) {
        if (initialized) {
            LOGGER.log(Level.SEVERE, message, t);
        } else {
            System.err.println("[ERROR] " + message);
            if (t != null) t.printStackTrace(System.err);
        }
    }

    private static class CustomFormatter extends Formatter {
        private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            String time = DATE_FORMAT.format(new Date(record.getMillis()));
            String level = record.getLevel().getName();
            String className = "Unknown";

            if (record.getSourceClassName() != null) {
                className = record.getSourceClassName().substring(
                        record.getSourceClassName().lastIndexOf('.') + 1);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%s] [%-7s] [%s] %s%n",
                    time, level, className, record.getMessage()));

            if (record.getThrown() != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                record.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                sb.append(sw.toString());
            }
            return sb.toString();
        }
    }
}
