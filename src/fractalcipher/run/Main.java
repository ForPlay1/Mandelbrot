package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.exception.ErrorHandler;
import fractalcipher.view.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Map;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        MainView view = new MainView();
        Scene scene = new Scene(view.getRoot(), 1300, 750);
        stage.setTitle("Фрактальное шифрование");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        try {
            CipherController controller = new CipherController();

            // Поток-воркер теперь работает постоянно
            Thread worker = new Thread(() -> {
                Map<Class<?>, Object> lastSelection = null;

                while (true) {
                    Map<Class<?>, Object> selection = MainView.getUserParams();

                    // Если данные появились и это НОВЫЙ объект (новое нажатие) - запускаем
                    if (selection != null && selection != lastSelection) {
                        lastSelection = selection;
                        controller.processSelection(selection);
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            });
            worker.setDaemon(true);
            worker.start();

            // Запускаем интерфейс
            launch(args);
        } catch (Throwable t) {
            System.err.println(ErrorHandler.handle(t));
            System.exit(1);
        }
    }
}