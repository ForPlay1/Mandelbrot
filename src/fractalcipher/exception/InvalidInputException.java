package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/** Ошибка ввода пользователя — можно показать детали. */
public class InvalidInputException extends FractalCipherException {
    public InvalidInputException(String message) {
        super(ErrorCategory.USER_INPUT, message);
    }
    public InvalidInputException(String message, Throwable cause) {
        super(ErrorCategory.USER_INPUT, message, cause);
    }
}