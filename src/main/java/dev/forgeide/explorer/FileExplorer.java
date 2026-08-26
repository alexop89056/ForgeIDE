package dev.forgeide.explorer;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

public final class FileExplorer extends VBox {
    private final TreeView<Path> tree = new TreeView<>();
    private final Consumer<Path> onFileOpen;

    public FileExplorer(Consumer<Path> onFileOpen) {
        this.onFileOpen = onFileOpen;
        getStyleClass().add("explorer");
        setPadding(new Insets(12));
        setSpacing(12);
        setPrefWidth(245);

        Label title = new Label("EXPLORER");
        title.getStyleClass().add("explorer-title");
        Button chooseFolder = new Button("Open folder…");
        chooseFolder.setMaxWidth(Double.MAX_VALUE);
        chooseFolder.setOnAction(event -> chooseFolder(getScene().getWindow()));

        tree.setShowRoot(true);
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
        setWorkspace(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize());
        getChildren().addAll(title, chooseFolder, tree);
        VBox.setVgrow(tree, Priority.ALWAYS);
    }

    private void chooseFolder(javafx.stage.Window owner) {
        Path current = tree.getRoot() == null ? Path.of(System.getProperty("user.home")) : tree.getRoot().getValue();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose workspace folder");
        if (Files.isDirectory(current)) chooser.setInitialDirectory(current.toFile());
        var selected = chooser.showDialog(owner);
        if (selected != null) setWorkspace(selected.toPath());
    }

    private void setWorkspace(Path path) {
        TreeItem<Path> root = treeItem(path);
        root.setExpanded(true);
        tree.setRoot(root);
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
