package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/** Запрошен неизвестный источник перестановки или диффузии. */
public class UnsupportedSourceException extends FractalCipherException {
    public UnsupportedSourceException(String message) {
        super(ErrorCategory.CONFIGURATION, message);
    }
}