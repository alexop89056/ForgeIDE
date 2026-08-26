package dev.forgeide.ui;

import dev.forgeide.editor.EditorTabs;
import dev.forgeide.editor.EditorTab;
import dev.forgeide.explorer.FileExplorer;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import dev.forgeide.preferences.WorkspacePreferences;
import dev.forgeide.preferences.EditorPreferences;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.SplitPane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class ForgeIdeWindow {
    private final Stage stage;
    private final Label status = new Label("Ready");
    private final EditorTabs tabs = new EditorTabs(status::setText);
    private final FileExplorer explorer = new FileExplorer(tabs::openPath);
    private final WorkspacePreferences preferences = new WorkspacePreferences();
    private final EditorPreferences editorPreferences = new EditorPreferences();
    private final Menu recentMenu = new Menu("Recent files");

    public ForgeIdeWindow(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.UNDECORATED);
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(createTopBar());
        root.setLeft(explorer);
        root.setCenter(tabs);
        root.setBottom(createStatusBar());
        var files = preferences.openFiles();
        files.forEach(tabs::openPath);

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/dev/forgeide/forgeide.css").toExternalForm());
        stage.setTitle("ForgeIDE");
        var logo = getClass().getResourceAsStream("/logo-transparent.png");
        if (logo != null) stage.getIcons().add(new Image(logo));
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (tabs.hasDirtyTabs() && !tabs.confirmCloseAll()) event.consume();
            else tabs.close();
        });
        stage.show();
        tabs.current().ifPresent(editor -> editor.focusEditor());
    }

    private HBox createTopBar() {
        Label title = new Label("ForgeIDE");
        title.getStyleClass().add("window-title");
        MenuBar menu = createMenuBar();
        Button minimize = windowButton("—", e -> stage.setIconified(true));
        Button maximize = windowButton("□", e -> stage.setMaximized(!stage.isMaximized()));
        Button close = windowButton("×", e -> stage.close());
        HBox bar = new HBox(title, menu, minimize, maximize, close);
        bar.getStyleClass().add("window-bar");
        HBox.setHgrow(menu, javafx.scene.layout.Priority.ALWAYS);
        final double[] offset = new double[2];
        bar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> { offset[0] = e.getSceneX(); offset[1] = e.getSceneY(); });
        bar.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> { if (!stage.isMaximized()) { stage.setX(e.getScreenX() - offset[0]); stage.setY(e.getScreenY() - offset[1]); } });
        return bar;
    }

    private Button windowButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text); button.getStyleClass().add("window-button"); button.setOnAction(action); return button;
    }

    private VBox createAgentPanel() {
        Label title = new Label("AGENT");
        title.getStyleClass().add("agent-title");
        Button close = new Button("×"); close.getStyleClass().add("agent-close");
        HBox header = new HBox(title, close); HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        TextArea messages = new TextArea("ForgeIDE Agent\n\nAsk anything about your code.");
        messages.setEditable(false); messages.setWrapText(true); messages.getStyleClass().add("agent-messages");
        TextField input = new TextField(); input.setPromptText("Message agent…");
        Button send = new Button("Send"); send.getStyleClass().add("agent-send");
        HBox composer = new HBox(input, send); HBox.setHgrow(input, javafx.scene.layout.Priority.ALWAYS);
        Runnable submit = () -> { if (!input.getText().isBlank()) { messages.appendText("\n\nYou: " + input.getText() + "\nAgent: Demo response (offline)"); input.clear(); } };
        send.setOnAction(e -> submit.run()); input.setOnAction(e -> submit.run());
        VBox panel = new VBox(10, header, messages, composer);
        panel.getStyleClass().add("agent-panel");
        panel.setPrefWidth(285);
        panel.setMinWidth(245);
        close.setOnAction(e -> { panel.setVisible(false); panel.setManaged(false); });
        return panel;
    }

    private MenuBar createMenuBar() {
        Menu file = new Menu("File");
        file.getItems().addAll(
                item("New", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), e -> tabs.newDocument()),
                item("Open folder…", null, e -> explorer.openFolder(stage)),
                item("New file", null, e -> explorer.createFile()),
                item("Refresh explorer", null, e -> explorer.refresh()),
                item("Open…", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), e -> { tabs.openDocument(stage); refreshRecent(); }),
                new SeparatorMenuItem(),
                item("Save", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), e -> tabs.saveCurrent(stage, false)),
                item("Save all", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), e -> tabs.saveAll(stage)),
                item("Save as…", null, e -> tabs.saveCurrent(stage, true)),
                new SeparatorMenuItem(),
                item("Close tab", new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), e -> tabs.closeCurrent()),
                recentMenu);
        refreshRecent();

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                item("Undo", new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), e -> tabs.current().ifPresent(editor -> editor.undo())),
                item("Redo", new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), e -> tabs.current().ifPresent(editor -> editor.redo())),
                item("Increase font", new KeyCodeCombination(KeyCode.PLUS, KeyCombination.SHORTCUT_DOWN), e -> tabs.current().ifPresent(EditorTab::increaseFont)),
                item("Decrease font", new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN), e -> tabs.current().ifPresent(EditorTab::decreaseFont)),
                item("Reset font", new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN), e -> tabs.current().ifPresent(EditorTab::resetFont)),
                new SeparatorMenuItem(),
                item("Find", new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), e -> find()),
                item("Replace", new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN), e -> replace()),
                item("Go to line", new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN), e -> gotoLine()));
        edit.getItems().add(item("Quick Open", new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), e -> quickOpen()));

        Menu view = new Menu("View");
        CheckMenuItem wrap = new CheckMenuItem("Wrap text");
        wrap.setSelected(editorPreferences.wrapText());
        wrap.setOnAction(e -> { editorPreferences.setWrapText(wrap.isSelected()); tabs.current().ifPresent(editor -> editor.setWrap(wrap.isSelected())); });
        Menu tabSize = new Menu("Tab size");
        ToggleGroup sizes = new ToggleGroup();
        for (int size : new int[]{2, 4, 8}) { RadioMenuItem item = new RadioMenuItem(String.valueOf(size)); item.setToggleGroup(sizes); item.setSelected(editorPreferences.tabSize() == size); item.setOnAction(e -> editorPreferences.setTabSize(size)); tabSize.getItems().add(item); }
        CheckMenuItem spaces = new CheckMenuItem("Insert spaces"); spaces.setSelected(editorPreferences.insertSpaces()); spaces.setOnAction(e -> editorPreferences.setInsertSpaces(spaces.isSelected()));
        Menu theme = new Menu("Theme");
        RadioMenuItem dark = new RadioMenuItem("Dark"); RadioMenuItem light = new RadioMenuItem("Light"); ToggleGroup themes = new ToggleGroup(); dark.setToggleGroup(themes); light.setToggleGroup(themes); dark.setSelected(true);
        dark.setOnAction(e -> applyTheme("dark")); light.setOnAction(e -> applyTheme("light")); theme.getItems().addAll(dark, light);
        view.getItems().addAll(wrap, tabSize, spaces, theme);
        return new MenuBar(file, edit, view);
    }

    private void applyTheme(String name) {
        editorPreferences.setTheme(name);
        if (stage.getScene() == null) return;
        stage.getScene().getStylesheets().removeIf(url -> url.endsWith("light.css"));
        if ("light".equals(name)) stage.getScene().getStylesheets().add(getClass().getResource("/dev/forgeide/light.css").toExternalForm());
    }

    private void refreshRecent() {
        recentMenu.getItems().clear();
        if (tabs.recentFiles().isEmpty()) { recentMenu.getItems().add(new MenuItem("No recent files")); return; }
        tabs.recentFiles().forEach(path -> {
            MenuItem item = new MenuItem(path.toString());
            item.setOnAction(e -> tabs.openPath(path));
            recentMenu.getItems().add(item);
        });
    }

    private void find() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Find"); dialog.setHeaderText("Find text");
        dialog.showAndWait().ifPresent(query -> tabs.current().ifPresent(editor -> editor.find(query)));
    }

    private void replace() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Replace");
        ButtonType replace = new ButtonType("Replace all", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(replace, ButtonType.CANCEL);
        GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.setPadding(new Insets(12));
        TextField find = new TextField(); TextField with = new TextField();
        grid.addRow(0, new Label("Find"), find); grid.addRow(1, new Label("Replace with"), with);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().filter(result -> result == replace).ifPresent(result -> tabs.current().ifPresent(editor -> editor.replaceAll(find.getText(), with.getText())));
    }

    private void gotoLine() {
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Go to line"); dialog.setHeaderText("Line number");
        dialog.showAndWait().ifPresent(value -> { try { tabs.current().ifPresent(editor -> editor.gotoLine(Integer.parseInt(value))); } catch (NumberFormatException ignored) { } });
    }

    private void quickOpen() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Quick Open"); dialog.setHeaderText("Type part of a file name");
        dialog.showAndWait().ifPresent(query -> {
            if (query.isBlank()) return;
            try (var paths = Files.walk(Path.of(System.getProperty("user.dir")))) {
                paths.filter(Files::isRegularFile).filter(path -> !path.toString().contains("/.git/"))
                        .filter(path -> path.getFileName().toString().toLowerCase().contains(query.toLowerCase()))
                        .findFirst().ifPresent(path -> { tabs.openPath(path); refreshRecent(); });
            } catch (Exception ignored) { }
        });
    }

    private MenuItem item(String label, KeyCombination accelerator, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem result = new MenuItem(label);
        if (accelerator != null) result.setAccelerator(accelerator);
        result.setOnAction(action);
        return result;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(status);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.getStyleClass().add("status-bar");
        return bar;
    }
}
