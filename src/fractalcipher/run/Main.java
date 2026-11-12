package fractalcipher.run;

import fractalcipher.controller.CipherController;
import fractalcipher.exception.ErrorHandler;
import fractalcipher.view.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Map;

/**
 * Точка входа JavaFX-приложения «Фрактальное шифрование».
 * <p>
 * Связывает три части архитектуры:
 * </p>
 * <ul>
 *   <li>{@link MainView} — UI и выбор пользователя (пишет в статическое
 *       поле {@link MainView#getUserParams()});</li>
 *   <li>{@link CipherController} — бизнес-логика (читает выбор, шифрует);</li>
 *   <li>worker-поток — мост между ними: опрашивает {@code getUserParams()}
 *       и передаёт новый выбор контроллеру.</li>
 * </ul>
 * <p>
 * <b>Зачем нужен worker-поток.</b> UI JavaFX выполняется в своём потоке
 * (JavaFX Application Thread), и тяжёлая работа (шифрование, бенчмарк)
 * в нём недопустима — интерфейс «замёрзнет». Поэтому {@code Main}
 * запускает демон-поток, который в цикле проверяет наличие нового
 * выбора и вызывает {@link CipherController#processSelection(Map)}
 * уже вне UI-потока.
 * </p>
 * <p>
 * <b>Как определяется «новый выбор».</b> {@code MainView} каждый раз
 * при нажатии «Запустить» создаёт <i>новый</i> объект {@code Map}.
 * Worker сравнивает ссылки ({@code selection != lastSelection}) —
 * если это новый объект, значит пользователь нажал «Запустить» ещё раз
 * (даже если параметры совпадают). Простое сравнение по содержимому
 * здесь не подошло бы: пользователь мог запустить ту же операцию
 * повторно, ожидая нового результата.
 * </p>
 *
 * @author FractalCipher Team
 * @see MainView
 * @see CipherController
 */
public class Main extends Application {

    /**
     * Стартует JavaFX-приложение: создаёт {@link MainView},
     * помещает его в {@link Scene} и показывает окно.
     *
     * @param stage главное окно (предоставляется JavaFX)
     */
    @Override
    public void start(Stage stage) {
        MainView view = new MainView();
        Scene scene = new Scene(view.getRoot(), 1300, 750);
        stage.setTitle("Фрактальное шифрование");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Запускает worker-поток и JavaFX-приложение.
     * <p>
     * Worker работает как демон ({@link Thread#setDaemon(boolean)}) —
     * завершится вместе с UI, не помешает выходу из приложения.
     * </p>
     * <p>
     * Если на этапе инициализации что-то пойдёт не так (например,
     * JavaFX не сможет запуститься), исключение пройдёт через
     * {@link ErrorHandler#handle(Throwable)} и приложение завершится
     * с кодом {@code 1}.
     * </p>
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        try {
            CipherController controller = new CipherController();

            // Поток-воркер работает постоянно: опрашивает getUserParams()
            // и запускает новую операцию при появлении нового объекта Map.
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