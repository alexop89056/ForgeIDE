package dev.forgeide.ui;

import dev.forgeide.editor.EditorTabs;
import dev.forgeide.explorer.FileExplorer;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class ForgeIdeWindow {
    private final Stage stage;
    private final Label status = new Label("Ready");
    private final EditorTabs tabs = new EditorTabs(status::setText);
    private final Menu recentMenu = new Menu("Recent files");

    public ForgeIdeWindow(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(createMenuBar());
        root.setLeft(new FileExplorer(tabs::openPath));
        root.setCenter(tabs);
        root.setBottom(createStatusBar());
        tabs.newDocument();

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/dev/forgeide/forgeide.css").toExternalForm());
        stage.setTitle("ForgeIDE");
        var logo = getClass().getResourceAsStream("/logo.png");
        if (logo != null) stage.getIcons().add(new Image(logo));
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (tabs.hasDirtyTabs() && !tabs.confirmCloseAll()) event.consume();
            else tabs.close();
        });
        stage.show();
        tabs.current().ifPresent(editor -> editor.focusEditor());
    }

    private MenuBar createMenuBar() {
        Menu file = new Menu("File");
        file.getItems().addAll(
                item("New", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), e -> tabs.newDocument()),
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
                new SeparatorMenuItem(),
                item("Find", new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), e -> find()),
                item("Replace", new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN), e -> replace()),
                item("Go to line", new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN), e -> gotoLine()));
        edit.getItems().add(item("Quick Open", new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), e -> quickOpen()));

        Menu view = new Menu("View");
        CheckMenuItem wrap = new CheckMenuItem("Wrap text");
        wrap.setOnAction(e -> tabs.current().ifPresent(editor -> editor.setWrap(wrap.isSelected())));
        view.getItems().add(wrap);
        return new MenuBar(file, edit, view);
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
