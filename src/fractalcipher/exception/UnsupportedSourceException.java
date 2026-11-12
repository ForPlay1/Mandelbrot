package fractalcipher.exception;

import fractalcipher.exception.ErrorHandler.ErrorCategory;

/**
 * Запрошен неизвестный источник перестановки или диффузии.
 * <p>
 * Бросается фабриками
 * {@link fractalcipher.model.permutation.PermutationSourceFactory} и
 * {@link fractalcipher.model.diffusion.DiffusionSourceFactory}, когда
 * по имени (например, {@code "mandelbrot"}) не находится зарегистрированный
 * источник. Основные причины:
 * </p>
 * <ul>
 *   <li>опечатка в имени источника в коде или в ключевом файле;</li>
 *   <li>файл {@code .key} зашифрован старой версией приложения, где
 *       источник назывался иначе (например, {@code "sierphinski"} до
 *       исправления опечатки);</li>
 *   <li>источник реализован, но забыт в конструкторе фабрики.</li>
 * </ul>
 * <p>
 * Категория — {@link ErrorHandler.ErrorCategory#CONFIGURATION}: это ошибка
 * настройки, а не пользовательского ввода. Пользователь не выбирает имя
 * источника напрямую — оно приходит из кода или из ключа.
 * </p>
 *
 * @author FractalCipher Team
 * @see ErrorHandler.ErrorCategory#CONFIGURATION
 */
public class UnsupportedSourceException extends FractalCipherException {

    /**
     * @param message описание: какое имя не найдено и в каком реестре
     */
    public UnsupportedSourceException(String message) {
        super(ErrorCategory.CONFIGURATION, message);
    }
}