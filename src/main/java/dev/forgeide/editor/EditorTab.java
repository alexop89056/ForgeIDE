package dev.forgeide.editor;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import dev.forgeide.syntax.SyntaxHighlighter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.IntStream;

public final class EditorTab extends Tab {
    private final CodeArea text = new CodeArea();
    private final TextArea lineNumbers = new TextArea("1");
    private final PauseTransition highlightDelay = new PauseTransition(Duration.millis(140));
    private Path path;

    public EditorTab(Path path, String content) {
        this.path = path;
        text.replaceText(content);
        text.getStyleClass().add("code-area");
        text.setStyle("-fx-font-family: 'JetBrains Mono'; -fx-font-size: 14px;");
        lineNumbers.setEditable(false);
        lineNumbers.setFocusTraversable(false);
        lineNumbers.setMouseTransparent(true);
        lineNumbers.getStyleClass().add("line-numbers");
        lineNumbers.setStyle("-fx-font-family: 'JetBrains Mono'; -fx-font-size: 14px;");

        ChangeListener<String> listener = (obs, old, value) -> {
            refreshLineNumbers();
            highlightDelay.playFromStart();
        };
        text.textProperty().addListener(listener);
        highlightDelay.setOnFinished(event -> applyHighlighting());
        refreshLineNumbers();
        applyHighlighting();

        HBox contentBox = new HBox(lineNumbers, text);
        contentBox.setPadding(new Insets(0));
        setContent(contentBox);
        HBox.setHgrow(text, Priority.ALWAYS);
        setText(title());
    }

    public Path getPath() { return path; }
    public String getDocumentText() { return text.getText(); }
    public String title() { return path == null ? "Untitled" : path.getFileName().toString(); }
    public void setWrap(boolean enabled) { text.setWrapText(enabled); }
    public void undo() { text.undo(); }
    public void redo() { text.redo(); }

    public void saveTo(Path target) {
        path = target;
        setText(title());
    }

    private void applyHighlighting() { text.setStyleSpans(0, SyntaxHighlighter.highlight(text.getText())); }

    private void refreshLineNumbers() {
        int lines = Math.max(1, text.getText().split("\\R", -1).length);
        lineNumbers.setText(IntStream.rangeClosed(1, lines).mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining("\n")));
    }
}
