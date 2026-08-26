package dev.forgeide.editor;

import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public final class EditorTabs extends TabPane {
    private final Consumer<String> onStatus;

    public EditorTabs(Consumer<String> onStatus) {
        this.onStatus = onStatus;
        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateStatus());
    }

    public void newDocument() {
        EditorTab editor = new EditorTab(null, "");
        getTabs().add(editor);
        getSelectionModel().select(editor);
    }

    public void openDocument(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open file");
        var selected = chooser.showOpenDialog(owner);
        if (selected != null) openPath(selected.toPath());
    }

    public void openPath(Path path) {
        try {
            EditorTab editor = new EditorTab(path, Files.readString(path));
            getTabs().add(editor);
            getSelectionModel().select(editor);
        } catch (IOException error) {
            showError("Could not open file", error);
        }
    }

    public void saveCurrent(Stage owner, boolean saveAs) {
        current().ifPresent(editor -> {
            Path target = editor.getPath();
            if (target == null || saveAs) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Save file");
                var selected = chooser.showSaveDialog(owner);
                if (selected == null) return;
                target = selected.toPath();
            }
            try {
                Files.writeString(target, editor.getDocumentText());
                editor.saveTo(target);
                onStatus.accept("Saved " + target.getFileName());
            } catch (IOException error) {
                showError("Could not save file", error);
            }
        });
    }

    public void closeCurrent() {
        if (getTabs().size() > 1) getTabs().remove(getSelectionModel().getSelectedItem());
    }

    public Optional<EditorTab> current() {
        return Optional.ofNullable(getSelectionModel().getSelectedItem()).map(tab -> (EditorTab) tab);
    }

    private void updateStatus() {
        current().ifPresent(editor -> onStatus.accept(editor.getPath() == null ? "Untitled" : editor.getPath().toString()));
    }

    private void showError(String message, Exception error) {
        new Alert(Alert.AlertType.ERROR, message + ": " + error.getMessage(), ButtonType.OK).showAndWait();
    }
}
