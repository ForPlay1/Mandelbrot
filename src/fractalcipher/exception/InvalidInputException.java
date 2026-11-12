package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/**
 * Ошибка ввода пользователя.
 * <p>
 * Используется, когда проблема в данных, которые ввёл пользователь:
 * несуществующий файл, неверный путь, отсутствующий ключ и т.п.
 * В отличие от {@link EncryptionFailedException}, сообщение такого
 * исключения <b>можно</b> показать пользователю почти дословно —
 * оно не содержит внутренних деталей реализации.
 * </p>
 *
 * @author FractalCipher Team
 * @see ErrorHandler.ErrorCategory#USER_INPUT
 */
public class InvalidInputException extends FractalCipherException {
    /**
     * @param message описание ошибки ввода (показывается пользователю)
     */
    public InvalidInputException(String message) {
        super(ErrorCategory.USER_INPUT, message);
    }
    /**
     * @param message описание ошибки ввода
     * @param cause   исходное исключение (например, {@link java.io.FileNotFoundException})
     */
    public InvalidInputException(String message, Throwable cause) {
        super(ErrorCategory.USER_INPUT, message, cause);
    }
}