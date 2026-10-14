package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/** Ошибка конфигурации — параметры ключа, режима, источника. */
public class InvalidConfigurationException extends FractalCipherException {
    public InvalidConfigurationException(String message) {
        super(ErrorCategory.CONFIGURATION, message);
    }
    public InvalidConfigurationException(String message, Throwable cause) {
        super(ErrorCategory.CONFIGURATION, message, cause);
    }
}