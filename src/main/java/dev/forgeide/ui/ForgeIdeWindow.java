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

public final class ForgeIdeWindow {
    private final Stage stage;
    private final Label status = new Label("Ready");
    private final EditorTabs tabs = new EditorTabs(status::setText);

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
        stage.setScene(scene);
        stage.show();
    }

    private MenuBar createMenuBar() {
        Menu file = new Menu("File");
        file.getItems().addAll(
                item("New", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), e -> tabs.newDocument()),
                item("Open…", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), e -> tabs.openDocument(stage)),
                new SeparatorMenuItem(),
                item("Save", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), e -> tabs.saveCurrent(stage, false)),
                item("Save as…", null, e -> tabs.saveCurrent(stage, true)),
                new SeparatorMenuItem(),
                item("Close tab", new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), e -> tabs.closeCurrent()));

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                item("Undo", new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), e -> tabs.current().ifPresent(editor -> editor.undo())),
                item("Redo", new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), e -> tabs.current().ifPresent(editor -> editor.redo())));

        Menu view = new Menu("View");
        CheckMenuItem wrap = new CheckMenuItem("Wrap text");
        wrap.setOnAction(e -> tabs.current().ifPresent(editor -> editor.setWrap(wrap.isSelected())));
        view.getItems().add(wrap);
        return new MenuBar(file, edit, view);
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
