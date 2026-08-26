package dev.forgeide.editor;

import dev.forgeide.lsp.LanguageServerManager;
import dev.forgeide.preferences.WorkspacePreferences;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TabPane.TabDragPolicy;
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
    private final WorkspacePreferences preferences = new WorkspacePreferences();

    public EditorTabs(Consumer<String> onStatus) {
        this.onStatus = onStatus;
        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        setTabDragPolicy(TabDragPolicy.REORDER);
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateStatus());
    }

    public void newDocument() {
        EditorTab editor = new EditorTab(null, "");
        register(editor);
        getSelectionModel().select(editor);
        rememberOpenFiles();
    }

    public void openDocument(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open file");
        var selected = chooser.showOpenDialog(owner);
        if (selected != null) openPath(selected.toPath());
    }

    public void openPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        for (Tab tab : getTabs()) {
            if (tab instanceof EditorTab editor && normalized.equals(editor.getPath())) {
                getSelectionModel().select(editor);
                return;
            }
        }
        try {
        EditorTab editor = new EditorTab(normalized, Files.readString(normalized));
            register(editor);
            recent.remove(normalized); recent.add(0, normalized);
            if (recent.size() > 10) recent.remove(10);
            String extension = extension(path);
            Path workspace = normalized.getParent() == null ? Path.of(System.getProperty("user.dir")) : normalized.getParent();
            languageServers.start(extension, workspace);
            languageServers.didOpen(normalized, languageId(extension), editor.getDocumentText());
            getSelectionModel().select(editor);
            rememberOpenFiles();
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
        if (getTabs().size() > 1) current().filter(EditorTab::confirmClose).ifPresent(editor -> { getTabs().remove(editor); rememberOpenFiles(); });
    }

    public void closeOthers(EditorTab keep) {
        for (EditorTab editor : getTabs().stream().filter(EditorTab.class::isInstance).map(EditorTab.class::cast).toList()) {
            if (editor != keep && editor.confirmClose()) getTabs().remove(editor);
        }
        getSelectionModel().select(keep); rememberOpenFiles();
    }

    public void closeAll() {
        if (!confirmCloseAll()) return;
        getTabs().clear(); rememberOpenFiles();
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

    public void rememberOpenFiles() {
        preferences.rememberOpenFiles(getTabs().stream().filter(EditorTab.class::isInstance).map(EditorTab.class::cast)
                .map(EditorTab::getPath).filter(java.util.Objects::nonNull).toList());
    }

    public Optional<EditorTab> current() {
        return Optional.ofNullable(getSelectionModel().getSelectedItem()).map(tab -> (EditorTab) tab);
    }

    private void register(EditorTab editor) {
        editor.setCaretListener(ignored -> updateStatus());
        editor.setDocumentChangeListener(text -> {
            if (editor.getPath() != null) languageServers.didChange(editor.getPath(), text);
        });
        editor.setOnCloseRequest(event -> {
            if (!editor.confirmClose()) event.consume(); else Platform.runLater(this::rememberOpenFiles);
        });
        MenuItem close = new MenuItem("Close");
        close.setOnAction(event -> { if (editor.confirmClose()) getTabs().remove(editor); });
        MenuItem closeOthers = new MenuItem("Close others");
        closeOthers.setOnAction(event -> closeOthers(editor));
        MenuItem closeAll = new MenuItem("Close all");
        closeAll.setOnAction(event -> closeAll());
        editor.setContextMenu(new ContextMenu(close, closeOthers, closeAll));
        getTabs().add(editor);
    }

    private static String languageId(String extension) {
        return switch (extension.toLowerCase()) { case "java" -> "java"; case "js", "jsx", "ts" -> "javascript"; case "py", "python" -> "python"; default -> extension; };
    }

    private void updateStatus() {
        current().ifPresent(editor -> onStatus.accept((editor.getPath() == null ? "Untitled" : compactPath(editor.getPath())) + "   ·   " + editor.caretStatus()));
    }

    private static String compactPath(Path path) {
        Path file = path.getFileName();
        Path parent = path.getParent();
        if (parent == null) return file == null ? path.toString() : file.toString();
        Path project = parent.getFileName();
        return (project == null ? "…" : project.toString()) + "/" + (file == null ? "" : file);
    }

    private void showError(String message, Exception error) {
        new Alert(Alert.AlertType.ERROR, message + ": " + error.getMessage(), ButtonType.OK).showAndWait();
    }

    @Override public void close() { rememberOpenFiles(); languageServers.close(); }
}
