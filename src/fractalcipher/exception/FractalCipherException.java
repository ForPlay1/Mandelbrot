package fractalcipher.exception;

/**
 * Базовое проверяемое-как-непроверяемое исключение приложения.
 * <p>
 * Наследуется от {@link RuntimeException}, чтобы не загромождать сигнатуры
 * методов объявлениями {@code throws} — ошибки шифрования, конфигурации
 * и ввода обрабатываются централизованно в
 * {@link ErrorHandler#handle(Throwable)}.
 * </p>
 * <p>
 * Каждое исключение несёт {@link ErrorHandler.ErrorCategory} — категорию,
 * по которой {@code ErrorHandler} выбирает уровень логирования и
 * «безопасное» сообщение для пользователя (без внутренних деталей
 * вроде стектрейсов и путей к файлам).
 * </p>
 *
 * @author FractalCipher Team
 * @see ErrorHandler
 * @see ErrorHandler.ErrorCategory
 */
public class FractalCipherException extends RuntimeException {

    /** Категория ошибки — определяет, как её логировать и что показать пользователю. */
    private final ErrorHandler.ErrorCategory category;

    /**
     * Создаёт исключение без причины.
     *
     * @param category категория ошибки (не {@code null})
     * @param message  описание для логов (может содержать технические детали)
     */
    public FractalCipherException(ErrorHandler.ErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    /**
     * Создаёт исключение с причиной.
     *
     * @param category категория ошибки (не {@code null})
     * @param message  описание для логов
     * @param cause    исходное исключение (для сохранения стектрейса)
     */
    public FractalCipherException(ErrorHandler.ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    /**
     * @return категория ошибки, по которой {@link ErrorHandler} выбирает
     *         уровень логирования и текст для пользователя
     */
    public ErrorHandler.ErrorCategory getCategory() {
        return category;
    }
}