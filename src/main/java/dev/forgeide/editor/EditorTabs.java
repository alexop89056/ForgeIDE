package dev.forgeide.editor;

import dev.forgeide.lsp.LanguageServerManager;
import javafx.scene.control.*;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EditorTabs extends TabPane implements AutoCloseable {
    private final Consumer<String> onStatus;
    private final List<Path> recent = new ArrayList<>();
    private final LanguageServerManager languageServers = new LanguageServerManager();

    public EditorTabs(Consumer<String> onStatus) {
        this.onStatus = onStatus;
        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateStatus());
    }

    public void newDocument() {
        EditorTab editor = new EditorTab(null, "");
        register(editor);
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
            register(editor);
            recent.remove(path); recent.add(0, path);
            if (recent.size() > 10) recent.remove(10);
            String extension = extension(path);
            languageServers.start(extension, path.getParent() == null ? path.toAbsolutePath().getParent() : path.getParent());
            getSelectionModel().select(editor);
        } catch (IOException error) {
            showError("Could not open file", error);
        }
    }

    public List<Path> recentFiles() { return List.copyOf(recent); }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
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

    public void saveAll(Stage owner) {
        for (Tab tab : List.copyOf(getTabs())) {
            if (tab instanceof EditorTab editor && editor.isDirty()) {
                Path target = editor.getPath();
                if (target == null) {
                    FileChooser chooser = new FileChooser(); chooser.setTitle("Save file");
                    var selected = chooser.showSaveDialog(owner); if (selected == null) continue; target = selected.toPath();
                }
                try { Files.writeString(target, editor.getDocumentText()); editor.saveTo(target); }
                catch (IOException error) { showError("Could not save file", error); }
            }
        }
    }

    public void closeCurrent() {
        if (getTabs().size() > 1) current().filter(EditorTab::confirmClose).ifPresent(getTabs()::remove);
    }

    public boolean confirmCloseAll() {
        for (Tab tab : List.copyOf(getTabs())) {
            if (tab instanceof EditorTab editor && !editor.confirmClose()) return false;
        }
        return true;
    }

    public boolean hasDirtyTabs() {
        return getTabs().stream().filter(EditorTab.class::isInstance)
                .map(EditorTab.class::cast).anyMatch(EditorTab::isDirty);
    }

    public Optional<EditorTab> current() {
        return Optional.ofNullable(getSelectionModel().getSelectedItem()).map(tab -> (EditorTab) tab);
    }

    private void register(EditorTab editor) {
        editor.setCaretListener(ignored -> updateStatus());
        editor.setOnCloseRequest(event -> {
            if (!editor.confirmClose()) event.consume();
        });
        getTabs().add(editor);
    }

    private void updateStatus() {
        current().ifPresent(editor -> onStatus.accept((editor.getPath() == null ? "Untitled" : editor.getPath().toString()) + "   ·   " + editor.caretStatus()));
    }

    private void showError(String message, Exception error) {
        new Alert(Alert.AlertType.ERROR, message + ": " + error.getMessage(), ButtonType.OK).showAndWait();
    }

    @Override public void close() { languageServers.close(); }
}
