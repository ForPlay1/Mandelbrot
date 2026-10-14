package fractalcipher.exception;

/**
 * Базовое исключение для всех ошибок шифра.
 * Наследуемся от RuntimeException, чтобы не загромождать
 * сигнатуры методов throws-объявлениями.
 */
public class FractalCipherException extends RuntimeException {

    private final ErrorHandler.ErrorCategory category;

    public FractalCipherException(ErrorHandler.ErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    public FractalCipherException(ErrorHandler.ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public ErrorHandler.ErrorCategory getCategory() {
        return category;
    }
}