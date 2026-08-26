package dev.forgeide.explorer;

import dev.forgeide.preferences.WorkspacePreferences;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.scene.control.TextInputDialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

public final class FileExplorer extends VBox {
    private final TreeView<Path> tree = new TreeView<>();
    private final Consumer<Path> onFileOpen;
    private final WorkspacePreferences preferences = new WorkspacePreferences();

    public FileExplorer(Consumer<Path> onFileOpen) {
        this.onFileOpen = onFileOpen;
        getStyleClass().add("explorer");
        setPadding(new Insets(12));
        setSpacing(12);
        setPrefWidth(245);

        Label title = new Label("EXPLORER");
        title.getStyleClass().add("explorer-title");
        Button newFile = new Button("New file");
        newFile.setMaxWidth(Double.MAX_VALUE);
        newFile.setDisable(true);
        newFile.setOnAction(event -> createFile());
        Button refresh = new Button("Refresh");
        refresh.setMaxWidth(Double.MAX_VALUE);
        refresh.setDisable(true);
        refresh.setOnAction(event -> refresh());

        tree.setShowRoot(true);
        preferences.lastWorkspace().ifPresent(this::setWorkspace);
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                setText(empty || path == null ? null : path.getFileName() == null ? path.toString() : path.getFileName().toString());
            }
        });
        tree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TreeItem<Path> selected = tree.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null && Files.isRegularFile(selected.getValue())) onFileOpen.accept(selected.getValue());
            }
        });
        getChildren().addAll(title, newFile, refresh, tree);
        if (tree.getRoot() != null) { newFile.setDisable(false); refresh.setDisable(false); }
        VBox.setVgrow(tree, Priority.ALWAYS);
    }

    private void chooseFolder(javafx.stage.Window owner) {
        Path current = tree.getRoot() == null ? Path.of(System.getProperty("user.home")) : tree.getRoot().getValue();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose workspace folder");
        if (Files.isDirectory(current)) chooser.setInitialDirectory(current.toFile());
        var selected = chooser.showDialog(owner);
        if (selected != null) {
            setWorkspace(selected.toPath());
            preferences.rememberWorkspace(selected.toPath());
            getChildren().stream().filter(node -> node instanceof Button button && button.getText().equals("New file"))
                    .forEach(node -> node.setDisable(false));
            getChildren().stream().filter(node -> node instanceof Button button && button.getText().equals("Refresh"))
                    .forEach(node -> node.setDisable(false));
        }
    }

    public void openFolder(javafx.stage.Window owner) { chooseFolder(owner); }

    private void setWorkspace(Path path) {
        TreeItem<Path> root = treeItem(path);
        root.setExpanded(true);
        tree.setRoot(root);
    }

    private void refresh() {
        if (tree.getRoot() == null) return;
        Path rootPath = tree.getRoot().getValue();
        tree.setRoot(treeItem(rootPath));
        tree.getRoot().setExpanded(true);
    }

    private void createFile() {
        if (tree.getRoot() == null || !Files.isDirectory(tree.getRoot().getValue())) return;
        TextInputDialog dialog = new TextInputDialog("untitled.txt");
        dialog.setTitle("New file");
        dialog.setHeaderText("Create a file in the workspace root");
        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank() || name.contains("/") || name.contains("\\")) return;
            Path file = tree.getRoot().getValue().resolve(name);
            try { Files.createFile(file); refresh(); onFileOpen.accept(file); }
            catch (IOException ignored) { }
        });
    }

    private TreeItem<Path> treeItem(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        if (Files.isDirectory(path)) item.getChildren().add(new TreeItem<>());
        item.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
            if (isExpanded && item.getChildren().size() == 1 && item.getChildren().get(0).getValue() == null) populate(item);
        });
        return item;
    }

    private void populate(TreeItem<Path> parent) {
        parent.getChildren().clear();
        try (var paths = Files.list(parent.getValue())) {
            paths.filter(path -> !path.getFileName().toString().equals(".git"))
                    .sorted(Comparator.comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(path -> parent.getChildren().add(treeItem(path)));
        } catch (IOException ignored) { }
    }
}
