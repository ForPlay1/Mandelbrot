package fractalcipher.view;

import fractalcipher.domain.CipherMode;
import fractalcipher.domain.DiffusionMode;
import fractalcipher.domain.PermutationMode;
import fractalcipher.utils.CipherUtils;
import fractalcipher.utils.AppLogger;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Главный экран приложения (JavaFX): выбор режима, методов и файлов,
 * предпросмотр, кнопка «Запустить».
 * <p>
 * <b>Как MainView общается с контроллером.</b> Напрямую — никак.
 * При нажатии «Запустить» создаётся <i>новый</i> объект {@code Map}
 * и публикуется через статическое поле {@link #getUserParams()}.
 * Worker-поток в {@link fractalcipher.run.Main} замечает смену ссылки
 * и вызывает {@link fractalcipher.controller.CipherController#processSelection}.
 * Так UI остаётся в своём потоке, а тяжёлая работа идёт в фоне.
 * </p>
 * <p>
 * <b>Структура словаря {@code userParams}:</b>
 * </p>
 * <ul>
 *   <li>{@code CipherMode.class} → выбранный режим;</li>
 *   <li>{@code PermutationMode.class} → выбранный фрактал
 *       (только для режимов шифрования);</li>
 *   <li>{@code DiffusionMode.class} → выбранная карта
 *       (только для режимов шифрования);</li>
 *   <li>{@code File.class} → входной файл или папка;</li>
 *   <li>{@code Path.class} → папка результата (не нужна в бенчмарке);</li>
 *   <li>{@code File[].class} → исходные файлы, которые пользователь
 *       решил ПРОПУСТИТЬ при конфликте имён (пустой массив — заменять всё).</li>
 * </ul>
 *
 * @author FractalCipher Team
 * @see fractalcipher.run.Main
 * @see fractalcipher.controller.CipherController
 */
public class MainView {

    /** Корневой контейнер экрана. */
    private final BorderPane root = new BorderPane();

    /** Область предпросмотра выбранного изображения. */
    private final ImageView preview = new ImageView();

    // текущее состояние выбора
    /** Выбранный входной файл или папка. */
    private File selectedFile;
    /** Выбранная папка результата. */
    private File outputDir;
    /** Флаг: режим выбран (блокирует зону входа до этого момента). */
    private boolean modeChosen = false;
    /** Флаг: входной файл/папка выбраны. */
    private boolean inputChosen = false;
    /** Флаг: папка результата выбрана. */
    private boolean outputChosen = false;

    /** Группа кнопок режима. */
    private final ToggleGroup modeGroup = new ToggleGroup();
    /** Группа кнопок источника перестановки. */
    private final ToggleGroup permGroup = new ToggleGroup();
    /** Группа кнопок источника диффузии. */
    private final ToggleGroup diffGroup = new ToggleGroup();

    /** Метка под зоной входа: показывает выбранный файл/папку. */
    private final Label fileLabel = new Label("Файл не выбран");
    /** Метка под зоной выхода: показывает выбранную папку результата. */
    private final Label outputLabel = new Label("Папка результата не выбрана");
    /** Поддерживаемые расширения изображений (для FileChooser и подсказок). */
    private final List<String> image_extensions = fractalcipher.domain.Image.getImageExtensions();

    // ссылки на элементы, чтобы управлять их доступностью
    /** Сетка кнопок перестановки — для управления доступностью. */
    private GridPane permutationGrid;
    /** Сетка кнопок диффузии — для управления доступностью. */
    private GridPane diffusionGrid;
    /** Кнопка «Запустить». */
    private Button runButton;
    /** Подсказка в зоне входа. */
    private Label dropHint;
    /** Кнопка выбора входного файла/папки. */
    private Button chooseButton;
    /** Контейнер зоны входа (для drag&drop и disable). */
    private VBox dropZoneBox;

    /** Подсказка в зоне выхода. */
    private Label outputHint;
    /** Кнопка выбора папки результата. */
    private Button chooseOutputButton;
    /** Контейнер зоны выхода. */
    private VBox outputZoneBox;

    /**
     * СЛОВАРЬ "КНОПКА → ЗНАЧЕНИЕ", который строится при нажатии «Запустить».
     */
    private final Map<ToggleButton, Enum<?>> selectionByButton = new HashMap<>();

    /**
     * "ЧИСТЫЙ" СЛОВАРЬ ДЛЯ ПЕРЕДАЧИ В Main:
     *  CipherMode.class     → выбранный режим
     *  PermutationMode.class→ выбранный источник перестановки
     *  DiffusionMode.class  → выбранный источник диффузии
     *  File.class           → входной файл или папка
     *  Path.class           → папка, куда класть результат
     */
    private static Map<Class<?>, Object> userParams = null;

    /**
     * @return последний опубликованный выбор (может быть {@code null},
     *         если пользователь ещё ни разу не нажимал «Запустить»)
     */
    public static Map<Class<?>, Object> getUserParams() {
        return userParams;
    }

    /** Обработчики выбора — сейчас только пишут в консоль (для отладки UI). */
    private final Map<Enum<?>, Consumer<Enum<?>>> handlers = new HashMap<>();

    /**
     * Создаёт экран: регистрирует обработчики, строит левую колонку
     * и область предпросмотра.
     */
    public MainView() {
        registerHandlers();
        root.setLeft(buildSidebar());
        root.setCenter(buildPreviewArea());
        updateEnabledState();
    }

    /**
     * @return корневой узел для помещения в {@link javafx.scene.Scene}
     */
    public Parent getRoot() {
        return root;
    }

    // ==========================================================
    //  РЕГИСТРАЦИЯ ОБРАБОТЧИКОВ
    // ==========================================================

    /**
     * Заполняет карту обработчиков выбора.
     * <p>
     * Сейчас обработчики просто логируют выбор в консоль — это удобно
     * для отладки UI. Сама логика (что делать при выборе режима) — в
     * {@link #onOptionSelected(ToggleButton)}.
     * </p>
     */
    private void registerHandlers() {
        handlers.put(CipherMode.ENCRYPT,   key -> System.out.println("Режим: Шифрование"));
        handlers.put(CipherMode.DECRYPT,   key -> System.out.println("Режим: Расшифровка"));
        handlers.put(CipherMode.BENCHMARK, key -> System.out.println("Режим: Бенчмарк"));
        handlers.put(CipherMode.BENCRYPT,  key -> System.out.println("Режим: Бенчмарк+Шифрование"));
        handlers.put(CipherMode.BENDECRYPT, key ->System.out.println("Режим: Расшифровка папки"));

        handlers.put(PermutationMode.MANDELBROT,   key -> System.out.println("Перестановка: Мандельброт"));
        handlers.put(PermutationMode.JULIA,        key -> System.out.println("Перестановка: Жюлиа"));
        handlers.put(PermutationMode.CANTOR,       key -> System.out.println("Перестановка: Кантор"));
        handlers.put(PermutationMode.BURNING_SHIP, key -> System.out.println("Перестановка: Горящий корабль"));
        // НОВЫЕ ОБРАБОТЧИКИ:
        handlers.put(PermutationMode.SIERPINSKI,   key -> System.out.println("Перестановка: Серпинский"));
        handlers.put(PermutationMode.DRAGON,       key -> System.out.println("Перестановка: Дракон"));

        handlers.put(DiffusionMode.LOGISTIC, key -> System.out.println("Диффузия: Логистическая"));
        handlers.put(DiffusionMode.HENON,    key -> System.out.println("Диффузия: Хенон"));
        handlers.put(DiffusionMode.TENT,     key -> System.out.println("Диффузия: Тент"));
        handlers.put(DiffusionMode.LORENZ,   key -> System.out.println("Диффузия: Лоренц"));
        handlers.put(DiffusionMode.ROSSLER,  key -> System.out.println("Диффузия: Рёсслер"));
        handlers.put(DiffusionMode.ARNOLD,   key -> System.out.println("Диффузия: Арнольд"));
        handlers.put(DiffusionMode.NULL,     key -> System.out.println("Диффузия: Нет"));
    }

    // ==========================================================
    //  ОБЩИЙ СЛУШАТЕЛЬ
    // ==========================================================

    /**
     * Реакция на выбор любой кнопки (режим / перестановка / диффузия).
     * <p>
     * Если выбрано новое значение {@link CipherMode}, сбрасывает
     * входной файл, выходную папку и предпросмотр: они зависят от режима
     * (для бенчмарка нужен любой вход, для пакетного — папка), и
     * «старый» выбор может стать несовместимым.
     * </p>
     *
     * @param btn нажатая кнопка (её {@code getUserData()} — значение enum)
     */
    private void onOptionSelected(ToggleButton btn) {
        Object key = btn.getUserData();
        if (!(key instanceof Enum<?> enumKey)) return;

        Consumer<Enum<?>> handler = handlers.get(enumKey);
        if (handler != null) handler.accept(enumKey);
        else System.out.println("Нет обработчика для: " + enumKey);

        if (enumKey instanceof CipherMode) {
            modeChosen = true;
            // смена режима сбрасывает и вход, и выход — они зависят от режима
            inputChosen = false;
            outputChosen = false;
            selectedFile = null;
            outputDir = null;
            fileLabel.setText(isFolderMode() ? "Папка не выбрана" : "Файл не выбран");
            outputLabel.setText("Папка результата не выбрана");
            preview.setImage(null);
        }

        updateEnabledState();
    }

    // ==========================================================
    //  СЛОВАРЬ "КНОПКА → ЗНАЧЕНИЕ"
    // ==========================================================

    /**
     * Собирает карту «нажатая кнопка → значение enum» из всех трёх групп.
     * <p>
     * Если какая-то группа не имеет выбранной кнопки — показывает
     * диалог и возвращает {@code null}.
     * </p>
     *
     * @return карта выбора или {@code null}, если не всё выбрано
     */
    private Map<ToggleButton, Enum<?>> buildSelectionMap() {
        selectionByButton.clear();

        ToggleGroup[] groups = { modeGroup, permGroup, diffGroup };
        String[]   names    = { "режим", "источник перестановки", "источник диффузии" };

        for (int i = 0; i < groups.length; i++) {
            Toggle selected = groups[i].getSelectedToggle();
            if (!(selected instanceof ToggleButton btn)) {
                showAlert("Не выбран " + names[i]);
                return null;
            }
            Object value = btn.getUserData();
            if (!(value instanceof Enum<?> enumValue)) {
                showAlert("У кнопки нет значения: " + btn.getText());
                return null;
            }
            selectionByButton.put(btn, enumValue);
        }

        return selectionByButton;
    }

    /**
     * Возвращает первое значение указанного enum из карты выбора.
     *
     * @param enumClass класс enum
     * @param <E>       тип enum
     * @return значение или {@code null}, если такого типа в карте нет
     */
    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E selectedValue(Class<E> enumClass) {
        for (Enum<?> value : selectionByButton.values()) {
            if (enumClass.isInstance(value)) {
                return (E) value;
            }
        }
        return null;
    }

    /** @return выбранный режим или {@code null}, если ещё не выбран */
    private CipherMode getSelectedMode() {
        if (modeGroup.getSelectedToggle() instanceof ToggleButton btn) {
            return (CipherMode) btn.getUserData();
        }
        return null;
    }

    /** @return {@code true}, если выбран пакетный режим (нужна папка, а не файл) */
    private boolean isFolderMode() {
        CipherMode m = getSelectedMode();
        return m == CipherMode.BENCRYPT || m == CipherMode.BENDECRYPT;
    }

    // ==========================================================
    //  ЛЕВАЯ КОЛОНКА
    // ==========================================================

    /**
     * Строит левую панель: зона входа, режимы, методы, зона выхода,
     * кнопка «Запустить».
     * <p>
     * Оборачивает в {@link ScrollPane} — на маленьких экранах все
     * элементы не влезают, и без прокрутки нижние кнопки были бы
     * недоступны.
     * </p>
     */
    private Node buildSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(320);
        sidebar.setStyle("-fx-background-color: #2b2b2b;");

        sidebar.getChildren().addAll(
                buildDropZone(),
                fileLabel,
                new Separator(),
                section("Режим", buildModeButtons()),
                section("Источник перестановки", buildPermutationButtons()),
                section("Источник диффузии", buildDiffusionButtons()),
                new Separator(),
                buildOutputZone(),
                outputLabel,
                new Separator(),
                buildRunButton()
        );

        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(340);
        scroll.setStyle("-fx-background: #2b2b2b;");
        return scroll;
    }

    /**
     * Создаёт секцию «заголовок + содержимое».
     *
     * @param title   заголовок (белый, жирный)
     * @param content содержимое под заголовком
     * @return вертикальный контейнер
     */
    private VBox section(String title, Node content) {
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        return new VBox(6, lbl, content);
    }

    // ==========================================================
    //  ЗОНА DRAG & DROP (ВХОД)
    // ==========================================================

    /**
     * Строит зону входа: пунктирная рамка, подсказка и кнопка выбора.
     * <p>
     * Поддерживает drag & drop файлов и папок. Правила проверки
     * зависят от режима:
     * </p>
     * <ul>
     *   <li>{@link CipherMode#BENCHMARK} — можно и файл, и папку;</li>
     *   <li>пакетные режимы — только папку;</li>
     *   <li>одиночные — только файл.</li>
     * </ul>
     * <p>
     * До выбора режима зона отключена ({@code modeChosen == false}) —
     * иначе пользователь мог бы перетащить файл, не зная, какую
     * операцию он запускает.
     * </p>
     */
    private Node buildDropZone() {
        dropZoneBox = new VBox(8);
        dropZoneBox.setAlignment(Pos.CENTER);
        dropZoneBox.setPrefHeight(120);
        dropZoneBox.setStyle(
                "-fx-border-color: #888; -fx-border-style: dashed; " +
                        "-fx-border-width: 2; -fx-border-radius: 8; " +
                        "-fx-background-color: #3a3a3a; -fx-background-radius: 8;"
        );

        dropHint = new Label("Сначала выберите режим");
        dropHint.setStyle("-fx-text-fill: #ddd; -fx-text-alignment: center;");
        dropHint.setWrapText(true);

        chooseButton = new Button("Выбрать входной файл/папку");
        chooseButton.setDisable(true);
        chooseButton.setOnAction(e -> onChooseInput());

        dropZoneBox.getChildren().addAll(dropHint, chooseButton);

        dropZoneBox.setOnDragOver(event -> {
            if (!modeChosen) { event.consume(); return; }
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        dropZoneBox.setOnDragDropped(event -> {
            if (!modeChosen) { event.setDropCompleted(false); event.consume(); return; }
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                File f = db.getFiles().get(0);
                CipherMode mode = selectedValue(CipherMode.class);

                // Если это БЕНЧМАРК — разрешаем кидать и папки, и файлы
                if (mode == CipherMode.BENCHMARK) {
                    if (f.isDirectory()) onFolderSelected(f);
                    else if (f.isFile()) onFileSelected(f);
                }
                // Для остальных режимов проверяем isFolderMode()
                else if (isFolderMode()) {
                    if (f.isDirectory()) onFolderSelected(f);
                    else showAlert("В этом режиме нужно перетащить папку");
                } else {
                    if (f.isFile()) onFileSelected(f);
                    else showAlert("Перетащите файл, а не папку");
                }
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });

        return dropZoneBox;
    }

    // ==========================================================
    //  ЗОНА ВЫБОРА ПАПКИ РЕЗУЛЬТАТА
    // ==========================================================

    /**
     * Строит зону выбора папки результата.
     * <p>
     * Скрывается в режиме бенчмарка ({@link #updateEnabledState()}
     * выставляет {@code visible=false, managed=false}) — бенчмарк
     * сохраняет CSV в {@code benchmarks/} относительно рабочей директории,
     * папка назначения ему не нужна.
     * </p>
     * <p>
     * Также поддерживает drag & drop — но только папок.
     * </p>
     */
    private Node buildOutputZone() {
        outputZoneBox = new VBox(8);
        outputZoneBox.setAlignment(Pos.CENTER);
        outputZoneBox.setPrefHeight(100);
        outputZoneBox.setStyle(
                "-fx-border-color: #888; -fx-border-style: dashed; " +
                        "-fx-border-width: 2; -fx-border-radius: 8; " +
                        "-fx-background-color: #3a3a3a; -fx-background-radius: 8;"
        );

        outputHint = new Label("Сначала выберите входной файл/папку");
        outputHint.setStyle("-fx-text-fill: #ddd; -fx-text-alignment: center;");
        outputHint.setWrapText(true);

        chooseOutputButton = new Button("Выбрать папку результата");
        chooseOutputButton.setDisable(true);
        chooseOutputButton.setOnAction(e -> onChooseOutput());

        outputZoneBox.getChildren().addAll(outputHint, chooseOutputButton);

        outputZoneBox.setOnDragOver(event -> {
            if (!modeChosen || !inputChosen) { event.consume(); return; }
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        outputZoneBox.setOnDragDropped(event -> {
            if (!modeChosen || !inputChosen) {
                event.setDropCompleted(false); event.consume(); return;
            }
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                File f = db.getFiles().get(0);
                if (f.isDirectory()) onOutputSelected(f);
                else showAlert("Для результата нужно перетащить папку");
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });

        return outputZoneBox;
    }

    // ==========================================================
    //  ГРУППЫ КНОПОК
    // ==========================================================

    /**
     * Строит группу кнопок режима.
     * <p>
     * Все кнопки в одной {@link ToggleGroup} — выбор одной снимает
     * выбор с других (это поведение радио-кнопок).
     * </p>
     */
    private Node buildModeButtons() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        addToggle(grid, "Шифрование",     CipherMode.ENCRYPT,    modeGroup, 0, 0);
        addToggle(grid, "Расшифровка",    CipherMode.DECRYPT,    modeGroup, 1, 0);
        addToggle(grid, "Бенчмарк",       CipherMode.BENCHMARK,  modeGroup, 0, 1);
        addToggle(grid, "Шифр. папки",    CipherMode.BENCRYPT,   modeGroup, 1, 1);
        addToggle(grid, "Расшиф. папки",  CipherMode.BENDECRYPT, modeGroup, 0, 2); // <-- НОВАЯ КНОПКА

        modeGroup.selectToggle(null);
        modeChosen = false;

        return grid;
    }

    /**
     * Строит группу кнопок выбора источника перестановки.
     */
    private Node buildPermutationButtons() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        addToggle(grid, "Мандельброт",     PermutationMode.MANDELBROT,   permGroup, 0, 0);
        addToggle(grid, "Жюлиа",           PermutationMode.JULIA,        permGroup, 1, 0);
        addToggle(grid, "Кантор",          PermutationMode.CANTOR,       permGroup, 0, 1);
        addToggle(grid, "Горящий корабль", PermutationMode.BURNING_SHIP, permGroup, 1, 1);
        // НОВЫЕ КНОПКИ:
        addToggle(grid, "Серпинский",      PermutationMode.SIERPINSKI,   permGroup, 0, 2);
        addToggle(grid, "Дракон",          PermutationMode.DRAGON,       permGroup, 1, 2);

        permutationGrid = grid;
        return grid;
    }

    /**
     * Строит группу кнопок выбора источника диффузии.
     */
    private Node buildDiffusionButtons() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        addToggle(grid, "Логистическая", DiffusionMode.LOGISTIC, diffGroup, 0, 0);
        addToggle(grid, "Хенон",         DiffusionMode.HENON,    diffGroup, 1, 0);
        addToggle(grid, "Тент",          DiffusionMode.TENT,     diffGroup, 0, 1);
        addToggle(grid, "Лоренц",        DiffusionMode.LORENZ,   diffGroup, 1, 1);
        addToggle(grid, "Рёсслер",       DiffusionMode.ROSSLER,  diffGroup, 0, 2);
        addToggle(grid, "Арнольд",       DiffusionMode.ARNOLD,   diffGroup, 1, 2);
        addToggle(grid, "Нет",           DiffusionMode.NULL,     diffGroup, 0, 3);

        diffusionGrid = grid;
        return grid;
    }

    /**
     * Создаёт кнопку-переключатель и добавляет её в сетку.
     * <p>
     * При выборе кнопка «подсвечивается» синим (акцентный цвет),
     * при снятии — возвращается к серому. Также вызывается
     * {@link #onOptionSelected(ToggleButton)}, чтобы сбросить
     * несовместимый выбор и обновить доступность элементов.
     * </p>
     *
     * @param grid   сетка, куда добавить кнопку
     * @param text   подпись кнопки
     * @param value  значение enum, связанное с кнопкой
     * @param group  группа (для радио-поведения)
     * @param col    колонка
     * @param row    строка
     */
    private void addToggle(GridPane grid, String text, Enum<?> value,
                           ToggleGroup group, int col, int row) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setUserData(value);
        btn.setPrefWidth(140);
        btn.setStyle("-fx-text-fill: white; -fx-background-color: #444;");

        btn.selectedProperty().addListener((obs, was, is) -> {
            if (is) {
                onOptionSelected(btn);
                btn.setStyle("-fx-text-fill: white; -fx-background-color: #0078d4;");
            } else {
                btn.setStyle("-fx-text-fill: white; -fx-background-color: #444;");
            }
        });

        grid.add(btn, col, row);
    }

    // ==========================================================
    //  КНОПКА "ЗАПУСТИТЬ"
    // ==========================================================

    /**
     * Создаёт кнопку «Запустить». Активна только когда всё выбрано
     * (см. {@link #updateEnabledState()}).
     */
    private Node buildRunButton() {
        runButton = new Button("Запустить");
        runButton.setPrefWidth(Double.MAX_VALUE);
        runButton.setStyle("-fx-background-color: #0078d4; -fx-text-fill: white; -fx-font-weight: bold;");
        runButton.setDisable(true);
        runButton.setOnAction(e -> onRun());
        return runButton;
    }

    // ==========================================================
    //  ОБЛАСТЬ ПРЕДПРОСМОТРА
    // ==========================================================

    /**
     * Строит область предпросмотра: картинка масштабируется под размер
     * панели с сохранением пропорций.
     */
    private Node buildPreviewArea() {
        StackPane pane = new StackPane(preview);
        pane.setStyle("-fx-background-color: #1e1e1e;");
        preview.setPreserveRatio(true);
        preview.fitWidthProperty().bind(pane.widthProperty().subtract(20));
        preview.fitHeightProperty().bind(pane.heightProperty().subtract(20));
        return pane;
    }

    // ==========================================================
    //  ЛОГИКА
    // ==========================================================

    /**
     * Обработчик нажатия «Выбрать входной файл/папку».
     * <p>
     * Для бенчмарка спрашивает диалогом, что тестировать (одиночный файл
     * или папку). Для остальных режимов — сразу открывает выбор файла
     * или папки, в зависимости от {@link #isFolderMode()}.
     * </p>
     */
    private void onChooseInput() {
        CipherMode mode = getSelectedMode();

        // Если выбран Бенчмарк — спрашиваем, файл или папку хочет пользователь
        if (mode == CipherMode.BENCHMARK) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Бенчмарк");
            alert.setHeaderText("Что вы хотите протестировать?");
            alert.setContentText("Выберите вариант:");

            ButtonType btnFile = new ButtonType("Одно изображение");
            ButtonType btnFolder = new ButtonType("Папка с изображениями");
            ButtonType btnCancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnFile, btnFolder, btnCancel);

            alert.showAndWait().ifPresent(type -> {
                if (type == btnFile) {
                    onChooseFile();
                } else if (type == btnFolder) {
                    onChooseFolder();
                }
            });
        } else if (isFolderMode()) {
            onChooseFolder();
        } else {
            onChooseFile();
        }
    }

    /** Открывает диалог выбора файла с фильтром по поддерживаемым расширениям. */
    private void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите изображение");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения",
                        image_extensions.stream().map(e -> "*." + e).toArray(String[]::new))
        );
        File f = chooser.showOpenDialog(getStage());
        if (f != null) onFileSelected(f);
    }

    /** Открывает диалог выбора папки с изображениями. */
    private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку с изображениями");
        File dir = chooser.showDialog(getStage());
        if (dir != null) onFolderSelected(dir);
    }

    /** Открывает диалог выбора папки результата. */
    private void onChooseOutput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку для результата");
        File dir = chooser.showDialog(getStage());
        if (dir != null) onOutputSelected(dir);
    }

    /**
     * Запоминает выбранный файл, показывает предпросмотр.
     *
     * @param file входной файл
     */
    private void onFileSelected(File file) {
        this.selectedFile = file;
        this.inputChosen = true;
        fileLabel.setText("Файл: " + file.getName());
        fileLabel.setStyle("-fx-text-fill: #9ad; -fx-wrap-text: true;");
        try {
            preview.setImage(new Image(file.toURI().toString()));
        } catch (Exception ex) {
            preview.setImage(null);
        }
        updateEnabledState();
    }

    /**
     * Запоминает выбранную папку в качестве входа. Предпросмотр
     * очищается: показывать «первую картинку из папки» было бы
     * произвольно.
     *
     * @param dir папка с изображениями
     */
    private void onFolderSelected(File dir) {
        this.selectedFile = dir;
        this.inputChosen = true;
        fileLabel.setText("Папка: " + dir.getName());
        fileLabel.setStyle("-fx-text-fill: #9ad; -fx-wrap-text: true;");
        preview.setImage(null);
        updateEnabledState();
    }

    /**
     * Запоминает папку результата.
     *
     * @param dir папка для сохранения
     */
    private void onOutputSelected(File dir) {
        this.outputDir = dir;
        this.outputChosen = true;
        outputLabel.setText("Результат: " + dir.getName());
        outputLabel.setStyle("-fx-text-fill: #9ad; -fx-wrap-text: true;");
        updateEnabledState();
    }

    /**
     * Обработчик нажатия «Запустить».
     * <p>
     * Порядок действий:
     * </p>
     * <ol>
     *   <li>Проверяет полноту выбора (режим, вход, выход — если нужен,
     *       методы — если нужны).</li>
     *   <li>Для не-бенчмарк режимов вызывает {@link #resolveConflicts}
     *       — пользователь разрешает коллизии имён в целевой папке.</li>
     *   <li>Строит <b>новый</b> объект {@code Map} и публикует его
     *       через {@link #userParams}.</li>
     *   <li>Печатает параметры в консоль для отладки.</li>
     * </ol>
     * <p>
     * Worker-поток в {@link fractalcipher.run.Main} увидит новую ссылку
     * и запустит обработку.
     * </p>
     */
    private void onRun() {
        CipherMode mode = getSelectedMode();
        if (mode == null) {
            showAlert("Ошибка: режим не распознан.");
            return;
        }

        // Для Бенчмарка НЕ ТРЕБУЕТСЯ папка результата
        boolean needOutput = (mode != CipherMode.BENCHMARK);

        // Проверяем базовые требования
        if (!modeChosen || !inputChosen || selectedFile == null || (needOutput && (!outputChosen || outputDir == null))) {
            showAlert("Выберите режим и входной файл/папку" + (needOutput ? " и папку результата" : ""));
            return;
        }

        // Бенчмарк сам перебирает все комбинации — методы не нужны
        boolean needMethods = (mode == CipherMode.ENCRYPT || mode == CipherMode.BENCRYPT);
        if (needMethods) {
            if (permGroup.getSelectedToggle() == null || diffGroup.getSelectedToggle() == null) {
                showAlert("Для шифрования необходимо выбрать методы перестановки и диффузии");
                return;
            }
        }

        // Разрешение конфликтов: пользователь выбирает «Заменить / Пропустить»
        Set<File> skipFiles = Collections.emptySet();
        if (mode != CipherMode.BENCHMARK) {
            Set<File> result = resolveConflicts(mode);
            if (result == null){
                AppLogger.warning("Запуск режима " + mode + " отменён пользователем "
                        + "из-за конфликтов имён.");
                System.out.println("Запуск отменён.");
                return;                       // Отмена — не запускаем
            }
            skipFiles = result;
        }

        // Новый объект Map = сигнал worker-потоку «есть работа»
        userParams = new HashMap<>();
        userParams.put(CipherMode.class, mode);
        userParams.put(File[].class, skipFiles.toArray(new File[0]));

        // Добавляем выбранные методы (если они есть)
        if (permGroup.getSelectedToggle() instanceof ToggleButton permBtn) {
            userParams.put(PermutationMode.class, permBtn.getUserData());
        }
        if (diffGroup.getSelectedToggle() instanceof ToggleButton diffBtn) {
            userParams.put(DiffusionMode.class, diffBtn.getUserData());
        }

        userParams.put(File.class, selectedFile);

        // Передаем папку результата только если она нужна
        if (needOutput) {
            userParams.put(Path.class, outputDir.toPath());
        }

        // Выводим информацию в консоль
        System.out.println("Запуск режима: " + mode);
        if (needMethods) {
            System.out.println("  перестановка = " + userParams.get(PermutationMode.class));
            System.out.println("  диффузия     = " + userParams.get(DiffusionMode.class));
        } else if (mode == CipherMode.BENCHMARK) {
            System.out.println("  методы будут перебраны автоматически");
        } else {
            System.out.println("  методы будут взяты из файлов ключей (.key)");
        }
        System.out.println("  вход         = " + selectedFile.getAbsolutePath());
        if (needOutput) {
            System.out.println("  результат    = " + outputDir.getAbsolutePath());
        }

        showAlert("Запуск: " + mode + ". Проверьте консоль.");
    }

    // ==========================================================
    //  РАЗРЕШЕНИЕ КОНФЛИКТОВ (диалог в стиле Windows)
    // ==========================================================

    /**
     * Результат одного диалога конфликта.
     *
     * @param replace    {@code true} — заменить существующий файл,
     *                   {@code false} — пропустить
     * @param applyToAll {@code true} — применить это же решение
     *                   к оставшимся конфликтам без диалогов
     */
    private record ConflictChoice(boolean replace, boolean applyToAll) {}

    /**
     * Обнаруживает конфликты имён в целевой папке и спрашивает
     * пользователя, как их разрешить.
     * <p>
     * Для каждого исходного файла вычисляется целевое имя через
     * {@link #computeTargetName} (то же правило, что и в
     * {@link fractalcipher.controller.CipherController}); если такой
     * файл уже существует — это конфликт.
     * </p>
     * <p>
     * Пользователю показывается по одному диалогу на конфликт — как
     * в проводнике Windows. Галочка «Применить ко всем» позволяет
     * не спрашивать про оставшиеся.
     * </p>
     *
     * @param mode режим (влияет на правило именования: encrypt/decrypt)
     * @return {@code null} — пользователь нажал «Отмена» на любом
     *         конфликте (вся операция отменяется);
     *         {@code Set<File>} — исходные файлы, которые нужно
     *         <b>пропустить</b>. Пустой набор = заменять всё.
     */
    private Set<File> resolveConflicts(CipherMode mode) {
        // 1. Собираем источники (как в старом askOverwritePolicy)
        List<File> sources = new ArrayList<>();
        if (mode == CipherMode.BENCRYPT || mode == CipherMode.BENDECRYPT) {
            File[] arr = selectedFile.listFiles(f -> f.isFile() && (
                    mode == CipherMode.BENCRYPT
                            ? CipherUtils.isImageToEncrypt(f.getName())
                            : f.getName().endsWith(CipherUtils.ENCRYPTED_SUFFIX + ".png")));
            if (arr != null) Collections.addAll(sources, arr);
        } else {
            sources.add(selectedFile);
        }

        // 2. Находим конфликты (цель уже существует) — в порядке обхода
        List<File> conflictSources = new ArrayList<>();
        Map<File, File> srcToTarget = new HashMap<>();
        for (File src : sources) {
            String targetName = computeTargetName(mode, src);
            File target = new File(outputDir, targetName);
            if (target.exists()) {
                conflictSources.add(src);
                srcToTarget.put(src, target);
            }
        }

        Set<File> toSkip = new HashSet<>();
        if (conflictSources.isEmpty()) {
            AppLogger.info("Конфликтов имён не обнаружено (режим " + mode
                    + ", папка: " + outputDir.getAbsolutePath() + ").");
            return toSkip;
        }

        AppLogger.warning(String.format(
                "Обнаружено конфликтов: %d из %d исходных файлов (режим %s, папка %s)",
                conflictSources.size(), sources.size(), mode, outputDir.getAbsolutePath()));

        // 3. По одному диалогу на каждый конфликт, пока не нажали «применить ко всем»
        Boolean applyToAllReplace = null;   // null — ещё не спрашивали
        for (int i = 0; i < conflictSources.size(); i++) {
            File src    = conflictSources.get(i);
            File target = srcToTarget.get(src);

            if (applyToAllReplace != null) {
                if (!applyToAllReplace) toSkip.add(src);
                String action = applyToAllReplace ? "Заменить" : "Пропустить";
                AppLogger.info(String.format(
                        "Конфликт %d/%d [%s]: авто-выбор — %s (по «применить ко всем»)",
                        i + 1, conflictSources.size(), src.getName(), action));
                continue;
            }

            AppLogger.info(String.format(
                    "Конфликт %d/%d: %s → %s (цель уже существует)",
                    i + 1, conflictSources.size(), src.getName(), target.getName()));

            ConflictChoice choice = showReplaceDialog(src, target, i + 1, conflictSources.size());
            if (choice == null){
                AppLogger.warning(String.format(
                        "Выбрана «Отмена» на конфликте %d/%d [%s] — операция отменена.",
                        i + 1, conflictSources.size(), src.getName()));
                return null;               // Отмена — прерываем всё
            }

            String action = choice.replace() ? "Заменить" : "Пропустить";
            AppLogger.info(String.format(
                    "Конфликт %d/%d [%s]: выбор — %s%s",
                    i + 1, conflictSources.size(), src.getName(), action,
                    choice.applyToAll() ? " (применить ко всем оставшимся)" : ""));

            if (!choice.replace()) toSkip.add(src);
            if (choice.applyToAll()){
                AppLogger.info(String.format(
                        "Дальнейшие конфликты (%d шт.) будут разрешены автоматически: «%s».",
                        conflictSources.size() - i - 1, action));
                applyToAllReplace = choice.replace();
            }
        }
        AppLogger.info(String.format(
                "Разрешение конфликтов завершено: заменено=%d, пропущено=%d.",
                conflictSources.size() - toSkip.size(), toSkip.size()));

        return toSkip;
    }

    /**
     * Считает имя целевого файла — <b>то же правило</b>, что использует
     * {@link fractalcipher.controller.CipherController}.
     * <p>
     * <b>Почему это критично.</b> Если правило здесь и в контроллере
     * разойдётся, проверка конфликтов будет проверять не те файлы,
     * которые реально пишет контроллер, — и пользователь молча
     * потеряет данные. Обе стороны должны использовать
     * {@link CipherUtils#safeStem} / {@link CipherUtils#baseName}
     * и одинаковый суффикс.
     * </p>
     *
     * @param mode режим (шифрование/расшифровка)
     * @param src  исходный файл
     * @return имя целевого файла (без пути)
     */
    private String computeTargetName(CipherMode mode, File src) {
        boolean decryptMode = (mode == CipherMode.DECRYPT || mode == CipherMode.BENDECRYPT);
        return decryptMode
                ? CipherUtils.baseName(src.getName()).replace(CipherUtils.ENCRYPTED_SUFFIX, "") + "_decrypted.png"
                : CipherUtils.safeStem(src.getName()) + CipherUtils.ENCRYPTED_SUFFIX + ".png";
    }

    /**
     * Показывает диалог «Файл уже существует» в стиле проводника Windows.
     * <p>
     * Содержит информацию о существующем и исходном файлах (размер,
     * дата, полный путь) и галочку «Применить ко всем оставшимся».
     * </p>
     *
     * @param source исходный файл
     * @param target существующий целевой файл
     * @param index  номер конфликта (1-based)
     * @param total  общее число конфликтов
     * @return выбор пользователя или {@code null}, если нажали «Отмена»
     */
    private ConflictChoice showReplaceDialog(File source, File target, int index, int total) {
        Dialog<ConflictChoice> dialog = new Dialog<>();
        dialog.setTitle("Файл уже существует");
        dialog.setHeaderText(null);

        // Иконка-предупреждение
        Label icon = new Label("⚠");
        icon.setStyle("-fx-font-size: 36px; -fx-text-fill: #f0a500; -fx-padding: 0 10 0 0;");
        dialog.getDialogPane().setGraphic(icon);

        ButtonType btnReplace = new ButtonType("Заменить", ButtonBar.ButtonData.YES);
        ButtonType btnSkip    = new ButtonType("Пропустить", ButtonBar.ButtonData.NO);
        ButtonType btnCancel  = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(btnReplace, btnSkip, btnCancel);

        Label header = new Label(
                "В этой папке уже есть файл с именем \"" + target.getName() + "\".");
        header.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label existingTitle = new Label("Существующий файл:");
        existingTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #0078d4;");
        Label existingInfo = new Label(
                "  " + formatSize(target.length())
                        + "   " + formatDate(target.lastModified())
                        + "\n  " + target.getAbsolutePath());
        existingInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        Label newTitle = new Label("Исходный файл:");
        newTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #0078d4;");
        Label newInfo = new Label(
                "  " + formatSize(source.length())
                        + "   " + formatDate(source.lastModified())
                        + "\n  " + source.getAbsolutePath());
        newInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        CheckBox applyToAll = new CheckBox();
        if (total <= 1) {
            // Единственный конфликт — «применить ко всем» бессмысленно
            applyToAll.setVisible(false);
            applyToAll.setManaged(false);
        } else {
            applyToAll.setText("Применить ко всем оставшимся файлам ("
                    + (total - index) + " из " + total + ")");
        }

        VBox content = new VBox(8,
                header,
                new Separator(),
                existingTitle, existingInfo,
                new Separator(),
                newTitle, newInfo,
                new Separator(),
                applyToAll
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(bt -> {
            if (bt == null || bt == btnCancel) return null;
            return new ConflictChoice(bt == btnReplace, applyToAll.isSelected());
        });

        return dialog.showAndWait().orElse(null);
    }

    /**
     * Форматирует размер файла для отображения пользователю.
     *
     * @param bytes размер в байтах
     * @return строка вида {@code "12 Б"}, {@code "1.2 КБ"}, {@code "3.4 МБ"}
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format("%.1f КБ", bytes / 1024.0);
        return String.format("%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    /**
     * Форматирует дату изменения файла для отображения пользователю.
     *
     * @param millis миллисекунды с epoch
     * @return строка в формате {@code dd.MM.yyyy HH:mm}
     */
    private static String formatDate(long millis) {
        return new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                .format(new java.util.Date(millis));
    }

    /** Показывает простое информационное сообщение. */
    private void showAlert(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    /** @return текущий {@link Stage} (для диалогов выбора файлов/папок) */
    private Stage getStage() {
        return (Stage) root.getScene().getWindow();
    }

    // ==========================================================
    //  УПРАВЛЕНИЕ ДОСТУПНОСТЬЮ
    // ==========================================================

    /**
     * Обновляет состояние всех интерактивных элементов в зависимости
     * от текущего выбора.
     * <p>
     * Ключевые правила:
     * </p>
     * <ul>
     *   <li>до выбора режима заблокированы и вход, и методы;</li>
     *   <li>методы нужны только для {@code ENCRYPT} и {@code BENCRYPT}
     *       (расшифровка берёт их из ключа, бенчмарк перебирает сам);</li>
     *   <li>зона выхода скрыта в бенчмарке ({@code visible=false,
     *       managed=false} — чтобы не занимала место в layout);</li>
     *   <li>кнопка «Запустить» активна только когда выбрано всё
     *       необходимое для текущего режима.</li>
     * </ul>
     * <p>
     * Вызывается после любого изменения выбора (кнопки, файл, папка)
     * и при старте.
     * </p>
     */
    private void updateEnabledState() {
        CipherMode mode = getSelectedMode();
        boolean needOutput = (mode != CipherMode.BENCHMARK);
        boolean needMethods = (mode == CipherMode.ENCRYPT || mode == CipherMode.BENCRYPT);

        // Перестановка и диффузия — только после выбора режима (и если они вообще нужны)
        setGridDisabled(permutationGrid, !modeChosen || !needMethods);
        setGridDisabled(diffusionGrid,   !modeChosen || !needMethods);

        // Зона входа — только после выбора режима
        if (dropZoneBox != null)  dropZoneBox.setDisable(!modeChosen);
        if (chooseButton != null) chooseButton.setDisable(!modeChosen);

        // Зона выхода — скрываем, если это Бенчмарк
        if (outputZoneBox != null) {
            outputZoneBox.setVisible(needOutput);
            outputZoneBox.setManaged(needOutput);
            outputZoneBox.setDisable(!needOutput || !(modeChosen && inputChosen));
        }
        if (chooseOutputButton != null) {
            chooseOutputButton.setDisable(!needOutput || !(modeChosen && inputChosen));
        }
        if (outputLabel != null) {
            outputLabel.setVisible(needOutput);
            outputLabel.setManaged(needOutput);
        }

        // Подсказки в зоне Drag & Drop
        if (dropHint != null) {
            if (!modeChosen) {
                dropHint.setText("Сначала выберите режим");
            } else if (mode == CipherMode.BENCHMARK) {
                dropHint.setText("Перетащите файл ИЛИ папку сюда\nПоддерживаемые форматы: "
                        + String.join(", ", image_extensions));
            } else if (isFolderMode()) {
                dropHint.setText("Перетащите папку с изображениями сюда\nПоддерживаемые форматы: "
                        + String.join(", ", image_extensions));
            } else {
                dropHint.setText("Перетащите файл сюда\nПоддерживаемые форматы: "
                        + String.join(", ", image_extensions));
            }
        }

        // Подсказки в зоне выхода (если она видима)
        if (outputHint != null && needOutput) {
            if (!modeChosen)          outputHint.setText("Сначала выберите режим");
            else if (!inputChosen)    outputHint.setText("Сначала выберите входной файл/папку");
            else                      outputHint.setText("Перетащите папку для результата сюда");
        }

        // «Запустить» — проверяем, что выбрано всё необходимое
        if (runButton != null) {
            boolean methodsChosen = true;
            if (needMethods) {
                methodsChosen = (permGroup.getSelectedToggle() != null) && (diffGroup.getSelectedToggle() != null);
            }

            boolean outputIsReady = needOutput ? outputChosen : true;

            // Кнопка активна, если выбран режим, вход, (выход, если нужен) и (методы, если нужны)
            runButton.setDisable(!(modeChosen && inputChosen && outputIsReady && methodsChosen));
        }
    }

    /**
     * Включает/выключает сетку кнопок и меняет прозрачность, чтобы
     * отключённое состояние было визуально понятно.
     *
     * @param grid     сетка (может быть {@code null} при первом вызове
     *                 до построения UI)
     * @param disabled {@code true} — заблокировать
     */
    private void setGridDisabled(GridPane grid, boolean disabled) {
        if (grid == null) return;
        grid.setDisable(disabled);
        grid.setOpacity(disabled ? 0.4 : 1.0);
    }
}