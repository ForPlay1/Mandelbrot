package fractalcipher.model;

import java.io.*;

/**
 * Утилита для сохранения и загрузки ключей шифрования в бинарные файлы.
 * <p>
 * Отвечает за формирование пути к файлу ключа (на основе пути к изображению),
 * а также за сериализацию/десериализацию объекта {@link EncryptionKey}.
 * </p>
 */
public class KeyManager {

    /**
     * Формирует путь к файлу ключа на основе пути к изображению.
     * <p>
     * Например: {@code C:\images\cat_encrypted.png} -> {@code C:\images\cat_encrypted.key}.
     * Файл ключа всегда лежит в той же директории, что и изображение.
     * </p>
     *
     * @param imageFile Файл зашифрованного изображения.
     * @return Файл ключа с расширением {@code .key}.
     */
    public static File getKeyFile(File imageFile){
        String name = imageFile.getName();
        int dot = name.lastIndexOf('.');
        // Если расширения нет — берем имя целиком, иначе отрезаем расширение
        String baseName = (dot == -1) ? name : name.substring(0,dot);
        return new File(imageFile.getParentFile(),baseName + ".key");
    }

    /**
     * Сохраняет ключ в бинарный файл.
     * <p>
     * Использует стандартную Java-сериализацию ({@link ObjectOutputStream}).
     * Файл создается или перезаписывается.
     * </p>
     *
     * @param key     Объект ключа для сохранения.
     * @param keyFile Файл, куда нужно сохранить ключ.
     * @throws IOException Если произошла ошибка ввода-вывода (нет прав, диск заполнен и т.д.).
     */
    public static void saveKey(EncryptionKey key, File keyFile) throws IOException{
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(keyFile))){
            oos.writeObject(key);
        }
    }

    /**
     * Загружает ключ из бинарного файла.
     *
     * @param keyFile Файл, откуда нужно прочитать ключ.
     * @return Восстановленный объект ключа.
     * @throws IOException            Если файл не найден или поврежден.
     * @throws ClassNotFoundException Если класс {@link EncryptionKey} недоступен
     *                                (например, при смене версии приложения).
     */
    public static EncryptionKey loadKey(File keyFile) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(keyFile))) {
            return (EncryptionKey) ois.readObject();
        }
    }
}
