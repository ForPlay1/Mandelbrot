package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/**
 * Ошибка в процессе шифрования или расшифровки.
 * <p>
 * Используется, когда сам алгоритм не смог отработать: траектория
 * хаотической карты разошлась (ушла в {@code NaN}/{@code Infinity}),
 * источник перестановки вернул массив неверной длины, поток диффузии
 * оказался короче, чем нужно для изображения, и т.п.
 * </p>
 * <p>
 * Сообщение содержит технические детали и предназначено для логов.
 * Пользователю через {@link ErrorHandler#handle(Throwable)} показывается
 * безопасная формулировка «Шифрование не удалось. Проверьте ключ и
 * входные данные.» — без стектрейсов и внутренних имён.
 * </p>
 *
 * @author FractalCipher Team
 * @see ErrorHandler.ErrorCategory#ENCRYPTION
 */
public class EncryptionFailedException extends FractalCipherException {

    /**
     * @param message описание причины сбоя шифрования
     */
    public EncryptionFailedException(String message) {
        super(ErrorCategory.ENCRYPTION, message);
    }

    /**
     * @param message описание причины сбоя шифрования
     * @param cause   исходное исключение (например, {@link ArithmeticException}
     *                или {@link ArrayIndexOutOfBoundsException} из источника)
     */
    public EncryptionFailedException(String message, Throwable cause) {
        super(ErrorCategory.ENCRYPTION, message, cause);
    }
}