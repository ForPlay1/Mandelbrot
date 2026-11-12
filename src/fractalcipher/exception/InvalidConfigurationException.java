package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/**
 * Ошибка конфигурации.
 * <p>
 * Возникает, когда параметры ключа, режима или выбранного источника
 * несовместимы с требованием алгоритма: например, параметр {@code mu}
 * тент-карты вне интервала {@code (1; 2)}, начальная точка карты Арнольда
 * равна {@code (0, 0)} или S-бокс получил некорректный seed.
 * </p>
 * <p>
 * В отличие от {@link InvalidInputException}, здесь виноват не пользователь,
 * а связка «ключ + алгоритм»: сообщение содержит технические детали
 * (имя параметра, допустимый диапазон) и полезно в логах, но пользователю
 * показывается в сокращённом виде.
 * </p>
 *
 * @author FractalCipher Team
 * @see ErrorHandler.ErrorCategory#CONFIGURATION
 */
public class InvalidConfigurationException extends FractalCipherException {

    /**
     * @param message описание проблемы конфигурации
     */
    public InvalidConfigurationException(String message) {
        super(ErrorCategory.CONFIGURATION, message);
    }

    /**
     * @param message описание проблемы конфигурации
     * @param cause   исходное исключение (например, {@link NumberFormatException}
     *                при разборе параметра)
     */
    public InvalidConfigurationException(String message, Throwable cause) {
        super(ErrorCategory.CONFIGURATION, message, cause);
    }
}