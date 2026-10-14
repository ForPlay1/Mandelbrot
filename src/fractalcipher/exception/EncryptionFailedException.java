package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/** Ошибка в процессе шифрования/расшифровки. */
public class EncryptionFailedException extends FractalCipherException {
    public EncryptionFailedException(String message) {
        super(ErrorCategory.ENCRYPTION, message);
    }
    public EncryptionFailedException(String message, Throwable cause) {
        super(ErrorCategory.ENCRYPTION, message, cause);
    }
}