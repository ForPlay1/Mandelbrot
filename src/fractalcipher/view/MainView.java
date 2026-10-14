package fractalcipher.view;

import fractalcipher.domain.CipherMode;
import fractalcipher.domain.DiffusionMode;
import fractalcipher.domain.PermutationMode;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MainView {

    private final BorderPane root = new BorderPane();
    private final ImageView preview = new ImageView();

    // текущее состояние выбора
    private File selectedFile;
    private File outputDir;
    private boolean modeChosen = false;
    private boolean inputChosen = false;
    private boolean outputChosen = false;

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleGroup permGroup = new ToggleGroup();
    private final ToggleGroup diffGroup = new ToggleGroup();

    private final Label fileLabel = new Label("Файл не выбран");
    private final Label outputLabel = new Label("Папка результата не выбрана");
    private final List<String> image_extensions = fractalcipher.domain.Image.getImageExtensions();

    // ссылки на элементы, чтобы управлять их доступностью
    private GridPane permutationGrid;
    private GridPane diffusionGrid;
    private Button runButton;
    private Label dropHint;
    private Button chooseButton;
    private VBox dropZoneBox;

    private Label outputHint;
    private Button chooseOutputButton;
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

    public static Map<Class<?>, Object> getUserParams() {
        return userParams;
    }

    private final Map<Enum<?>, Consumer<Enum<?>>> handlers = new HashMap<>();

    public MainView() {
        registerHandlers();
        root.setLeft(buildSidebar());
        root.setCenter(buildPreviewArea());
        updateEnabledState();
    }

    public Parent getRoot() {
        return root;
    }

    // ==========================================================
    //  РЕГИСТРАЦИЯ ОБРАБОТЧИКОВ
    // ==========================================================

    // логирование
    private void registerHandlers() {
        handlers.put(CipherMode.ENCRYPT,   key -> System.out.println("Режим: Шифрование"));
        handlers.put(CipherMode.DECRYPT,   key -> System.out.println("Режим: Расшифровка"));
        handlers.put(CipherMode.BENCHMARK, key -> System.out.println("Режим: Бенчмарк"));
        handlers.put(CipherMode.BENCRYPT,  key -> System.out.println("Режим: Бенчмарк+Шифрование"));

        handlers.put(PermutationMode.MANDELBROT,   key -> System.out.println("Перестановка: Мандельброт"));
        handlers.put(PermutationMode.JULIA,        key -> System.out.println("Перестановка: Жюлиа"));
        handlers.put(PermutationMode.CANTOR,       key -> System.out.println("Перестановка: Кантор"));
        handlers.put(PermutationMode.BURNING_SHIP, key -> System.out.println("Перестановка: Горящий корабль"));

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

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E selectedValue(Class<E> enumClass) {
        for (Enum<?> value : selectionByButton.values()) {
            if (enumClass.isInstance(value)) {
                return (E) value;
            }
        }
        return null;
    }

    private boolean isFolderMode() {
        CipherMode m = selectedValue(CipherMode.class);
        if (m != null) return m == CipherMode.BENCRYPT;

        if (modeGroup.getSelectedToggle() instanceof ToggleButton btn
                && btn.getUserData() instanceof CipherMode fromBtn) {
            return fromBtn == CipherMode.BENCRYPT;
        }
        return false;
    }

    // ==========================================================
    //  ЛЕВАЯ КОЛОНКА
    // ==========================================================

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

    private VBox section(String title, Node content) {
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        return new VBox(6, lbl, content);
    }

    // ==========================================================
    //  ЗОНА DRAG & DROP (ВХОД)
    // ==========================================================

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
                if (isFolderMode()) {
                    if (f.isDirectory()) onFolderSelected(f);
                    else showAlert("В режиме «Бенчмарк+Шифр» нужно перетащить папку");
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

    private Node buildModeButtons() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        addToggle(grid, "Шифрование",     CipherMode.ENCRYPT,   modeGroup, 0, 0);
        addToggle(grid, "Расшифровка",    CipherMode.DECRYPT,   modeGroup, 1, 0);
        addToggle(grid, "Бенчмарк",       CipherMode.BENCHMARK, modeGroup, 0, 1);
        addToggle(grid, "Бенчмарк+Шифр",  CipherMode.BENCRYPT,  modeGroup, 1, 1);

        modeGroup.selectToggle(null);
        modeChosen = false;

        return grid;
    }

    private Node buildPermutationButtons() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        addToggle(grid, "Мандельброт",     PermutationMode.MANDELBROT,   permGroup, 0, 0);
        addToggle(grid, "Жюлиа",           PermutationMode.JULIA,        permGroup, 1, 0);
        addToggle(grid, "Кантор",          PermutationMode.CANTOR,       permGroup, 0, 1);
        addToggle(grid, "Горящий корабль", PermutationMode.BURNING_SHIP, permGroup, 1, 1);

        permutationGrid = grid;
        return grid;
    }

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

    private void onChooseInput() {
        if (isFolderMode()) onChooseFolder();
        else                onChooseFile();
    }

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

    private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку с изображениями");
        File dir = chooser.showDialog(getStage());
        if (dir != null) onFolderSelected(dir);
    }

    private void onChooseOutput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку для результата");
        File dir = chooser.showDialog(getStage());
        if (dir != null) onOutputSelected(dir);
    }

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

    private void onFolderSelected(File dir) {
        this.selectedFile = dir;
        this.inputChosen = true;
        fileLabel.setText("Папка: " + dir.getName());
        fileLabel.setStyle("-fx-text-fill: #9ad; -fx-wrap-text: true;");
        preview.setImage(null);
        updateEnabledState();
    }

    private void onOutputSelected(File dir) {
        this.outputDir = dir;
        this.outputChosen = true;
        outputLabel.setText("Результат: " + dir.getName());
        outputLabel.setStyle("-fx-text-fill: #9ad; -fx-wrap-text: true;");
        updateEnabledState();
    }

    private void onRun() {
        if (!modeChosen || !inputChosen || !outputChosen
                || selectedFile == null || outputDir == null) {
            showAlert("Выберите режим, входной файл/папку и папку результата");
            return;
        }

        // === СТРОИМ СЛОВАРЬ "КНОПКА → ЗНАЧЕНИЕ" ===
        Map<ToggleButton, Enum<?>> map = buildSelectionMap();
        if (map == null) return;

        // === ПУБЛИКУЕМ "ЧИСТЫЙ" СЛОВАРЬ ДЛЯ Main ===
        userParams = new HashMap<>();
        for (Enum<?> v : map.values()) {
            userParams.put(v.getDeclaringClass(), v);
        }
        userParams.put(File.class, selectedFile);              // входной файл/папка
        userParams.put(Path.class, outputDir.toPath());        // папка результата

        CipherMode      mode   = selectedValue(CipherMode.class);
        PermutationMode perm   = selectedValue(PermutationMode.class);
        DiffusionMode   diff   = selectedValue(DiffusionMode.class);
        boolean         folder = mode == CipherMode.BENCRYPT;

        System.out.println("Словарь выбора:");
        map.forEach((btn, value) ->
                System.out.println("  [" + btn.getText() + "] → " + value));
        System.out.println("  вход     = " + selectedFile.getAbsolutePath()
                + (folder ? " (папка)" : " (файл)"));
        System.out.println("  результат= " + outputDir.getAbsolutePath());

        showAlert("Готово: " + mode + " / " + perm + " / " + diff);
    }

    private void showAlert(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    private Stage getStage() {
        return (Stage) root.getScene().getWindow();
    }

    // ==========================================================
    //  УПРАВЛЕНИЕ ДОСТУПНОСТЬЮ
    // ==========================================================

    private void updateEnabledState() {
        // перестановка и диффузия — только после выбора режима
        setGridDisabled(permutationGrid, !modeChosen);
        setGridDisabled(diffusionGrid,   !modeChosen);

        // зона входа — только после выбора режима
        if (dropZoneBox != null)  dropZoneBox.setDisable(!modeChosen);
        if (chooseButton != null) chooseButton.setDisable(!modeChosen);

        // зона выхода — только после выбора режима И входного файла/папки
        boolean outputEnabled = modeChosen && inputChosen;
        if (outputZoneBox != null)       outputZoneBox.setDisable(!outputEnabled);
        if (chooseOutputButton != null)  chooseOutputButton.setDisable(!outputEnabled);

        // подсказки
        if (dropHint != null) {
            if (!modeChosen) {
                dropHint.setText("Сначала выберите режим");
            } else if (isFolderMode()) {
                dropHint.setText("Перетащите папку с изображениями сюда\nПоддерживаемые форматы: "
                        + String.join(", ", image_extensions));
            } else {
                dropHint.setText("Перетащите файл сюда\nПоддерживаемые форматы: "
                        + String.join(", ", image_extensions));
            }
        }

        if (outputHint != null) {
            if (!modeChosen)          outputHint.setText("Сначала выберите режим");
            else if (!inputChosen)    outputHint.setText("Сначала выберите входной файл/папку");
            else                      outputHint.setText("Перетащите папку для результата сюда");
        }

        // «Запустить» — только когда есть режим, вход и выход
        if (runButton != null) {
            runButton.setDisable(!(modeChosen && inputChosen && outputChosen));
        }
    }

    private void setGridDisabled(GridPane grid, boolean disabled) {
        if (grid == null) return;
        grid.setDisable(disabled);
        grid.setOpacity(disabled ? 0.4 : 1.0);
    }
}