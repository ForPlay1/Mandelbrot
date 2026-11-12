package fractalcipher.utils;

import fractalcipher.exception.ErrorHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * Централизованный логгер приложения на базе {@link java.util.logging}.
 * <p>
 * При старте (в статическом инициализаторе) пытается создать три обработчика:
 * </p>
 * <ul>
 *   <li><b>app-*.log</b> — основной лог с ротацией: 5 МБ на файл, 5 файлов;</li>
 *   <li><b>critical.log</b> — отдельный файл для записей уровня {@code SEVERE};</li>
 *   <li><b>консоль</b> — дублирование в {@code System.err} для отладки.</li>
 * </ul>
 * <p>
 * <b>Почему есть флаг {@link #isInitialized()}.</b> Если создать папку
 * {@code logs/} или открыть файлы не удалось (нет прав, диск переполнен),
 * логгер не «падает» — он переходит в запасной режим: сообщения пишутся
 * в {@code System.out}/{@code System.err} без форматирования и без
 * записи в файл. Это лучше, чем потерять лог полностью.
 * </p>
 * <p>
 * <b>Shutdown hook.</b> При выходе JVM принудительно вызывает
 * {@code flush}/{@code close} у каждого обработчика — чтобы последние
 * строки не потерялись из-за буферизации.
 * </p>
 * <p>
 * Все методы статические и потокобезопасны (за счёт синхронизации внутри
 * {@link java.util.logging.Logger}).
 * </p>
 *
 * @author FractalCipher Team
 * @see ErrorHandler
 */
public class AppLogger {

    /** Имя логгера в глобальном реестре {@link java.util.logging.LogManager}. */
    private static final Logger LOGGER = Logger.getLogger("FractalCipher");

    /**
     * Флаг успешной инициализации.
     * <p>
     * {@code false} — файлы логов недоступны, все методы работают
     * в запасном режиме (вывод в консоль).
     * </p>
     */
    private static boolean initialized = false;

    /**
     * @return {@code true}, если файловые обработчики удалось создать
     *         и сообщения пишутся в файлы; {@code false} — работаем
     *         через консоль
     */
    public static boolean isInitialized(){
        return initialized;
    }

    /**
     * Статический инициализатор: создаёт папку {@code logs/}, подключает
     * файловые и консольный обработчики, регистрирует shutdown hook.
     * <p>
     * Если создать файлы не удалось — вызывает {@link ErrorHandler#handle},
     * который увидит {@code initialized == false} и выведет ошибку в
     * {@code System.err}, а не попытается писать в несуществующий лог.
     * </p>
     */
    static {
        try {
            Files.createDirectories(Paths.get("logs"));

            // Основной лог: ротация по 5 МБ, 5 файлов, дописывание в конец
            FileHandler fileHandler = new FileHandler(
                    "logs/app-%g.log",
                    5 * 1024 * 1024,  // 5 МБ
                    5,                 // 5 файлов (rotating)
                    true               // дописывание в конец
            );
            fileHandler.setFormatter(new CustomFormatter());
            fileHandler.setLevel(Level.INFO);

            // Отдельный файл для критических ошибок (SEVERE)
            FileHandler criticalHandler = new FileHandler("logs/critical.log", true);
            criticalHandler.setFormatter(new CustomFormatter());
            criticalHandler.setLevel(Level.SEVERE);

            // Консоль — видеть логи в IDE и терминале
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new CustomFormatter());
            consoleHandler.setLevel(Level.INFO);

            LOGGER.addHandler(fileHandler);
            LOGGER.addHandler(criticalHandler);
            LOGGER.addHandler(consoleHandler);
            LOGGER.setUseParentHandlers(false);

            // Shutdown hook: гарантирует flush буферов при выходе JVM
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (Handler h : LOGGER.getHandlers()) {
                    h.flush();
                    h.close();
                }
            }));

            initialized = true;
        }
        catch (IOException e) {
            // Файлы недоступны — переходим в запасной режим
            ErrorHandler.handle(e);
        }
    }

    /**
     * Записывает сообщение уровня {@code INFO}.
     *
     * @param message текст сообщения
     */
    public static void info(String message) {
        if (initialized) {
            LOGGER.log(Level.INFO, message);
        } else {
            // Запасной вариант, если файлы логов недоступны
            System.out.println("[INFO] " + message);
        }
    }

    /**
     * Записывает сообщение уровня {@code WARNING}.
     *
     * @param message текст сообщения
     */
    public static void warning(String message) {
        if (initialized) {
            LOGGER.log(Level.WARNING, message);
        } else {
            System.out.println("[WARNING] " + message);
        }
    }

    /**
     * Записывает сообщение уровня {@code SEVERE} вместе со стектрейсом.
     *
     * @param message описание ошибки
     * @param t       исключение (может быть {@code null} — тогда только сообщение)
     */
    public static void error(String message, Throwable t) {
        if (initialized) {
            LOGGER.log(Level.SEVERE, message, t);
        } else {
            System.err.println("[ERROR] " + message);
            if (t != null) t.printStackTrace(System.err);
        }
    }

    /**
     * Форматтер строк лога: {@code [время] [УРОВЕНЬ] [Класс] сообщение}.
     * <p>
     * Если у записи есть {@link LogRecord#getThrown()}, стектрейс
     * печатается отдельными строками ниже сообщения.
     * </p>
     */
    private static class CustomFormatter extends Formatter {

        /** Формат даты в строке лога. */
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
