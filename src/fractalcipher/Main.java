package fractalcipher;

import fractalcipher.controller.CipherController;
import fractalcipher.model.ChaosSourceFactory;
import fractalcipher.model.EncryptionKey;
import fractalcipher.model.ImageCipher;
import fractalcipher.model.ImageCipherImpl;
import fractalcipher.view.ConsoleView;
import fractalcipher.view.ImageView;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        ChaosSourceFactory factory = new ChaosSourceFactory();
        ImageCipher cipher = new ImageCipherImpl(factory);
        CipherController controller = new CipherController(cipher);
        ImageView view = new ConsoleView();

        BufferedImage image = view.requestInputImage();
        if (image == null) return;

        String sourceName = view.requestSourceName();
        Map<String, Double> params = new HashMap<>();
        EncryptionKey key = new EncryptionKey(sourceName, params);

        try {
            BufferedImage result = controller.handleEncrypt(image, key);
            view.showResult(result);
        } catch (UnsupportedOperationException e) {
            view.showError(e.getMessage());
        }
    }
}