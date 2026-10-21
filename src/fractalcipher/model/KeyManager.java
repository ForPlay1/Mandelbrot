package fractalcipher.model;

import java.io.*;

/**
 * Утилита для работы с бинарными файлами ключей.
 */
public class KeyManager {

    /**
     * Формирует путь к файлу ключа на основе пути к изображению.
     * Например: C:\images\cat_encrypted.png -> C:\images\cat_encrypted.key
     */
    public static File getKeyFile(File imageFile){
        String name = imageFile.getName();
        int dot = name.lastIndexOf('.');
        String baseName = (dot == -1) ? name : name.substring(0,dot);
        return new File(imageFile.getParentFile(),baseName + ".key");
    }

    /**
     * Сохраняет ключ в бинарный файл.
     */
    public static void saveKey(EncryptionKey key, File keyFile) throws IOException{
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(keyFile))){
            oos.writeObject(key);
        }
    }

    /**
     * Загружает ключ из бинарного файла
     */
    public static EncryptionKey loadKey(File keyFile) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(keyFile))) {
            return (EncryptionKey) ois.readObject();
        }
    }
}
