package dev.forgeide.editor;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import dev.forgeide.syntax.SyntaxHighlighter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public final class EditorTab extends Tab {
    private final CodeArea text = new CodeArea();
    private final TextArea lineNumbers = new TextArea("1");
    private final PauseTransition highlightDelay = new PauseTransition(Duration.millis(140));
    private Path path;
    private boolean dirty;
    private boolean initializing = true;
    private int highlightedParagraph = -1;
    private Consumer<EditorTab> caretListener = ignored -> { };

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
            if (!initializing) {
                dirty = true;
                setText(displayTitle());
            }
            highlightDelay.playFromStart();
        };
        text.textProperty().addListener(listener);
        text.caretPositionProperty().addListener((obs, old, value) -> {
            updateCurrentLine();
            caretListener.accept(this);
        });
        highlightDelay.setOnFinished(event -> applyHighlighting());
        refreshLineNumbers();
        updateCurrentLine();
        applyHighlighting();
        initializing = false;

        HBox contentBox = new HBox(lineNumbers, text);
        contentBox.setPadding(new Insets(0));
        setContent(contentBox);
        HBox.setHgrow(text, Priority.ALWAYS);
        setText(title());
    }

    public Path getPath() { return path; }
    public String getDocumentText() { return text.getText(); }
    public String title() { return path == null ? "Untitled" : path.getFileName().toString(); }
    public boolean isDirty() { return dirty; }
    public String displayTitle() { return title() + (dirty ? " ●" : ""); }
    public void setCaretListener(Consumer<EditorTab> listener) { caretListener = listener == null ? ignored -> { } : listener; }
    public void setWrap(boolean enabled) { text.setWrapText(enabled); }
    public void undo() { text.undo(); }
    public void redo() { text.redo(); }
    public void focusEditor() { text.requestFocus(); }
    public void find(String query) {
        if (query == null || query.isBlank()) return;
        int start = Math.min(text.getCaretPosition() + 1, text.getLength());
        int index = text.getText().indexOf(query, start);
        if (index < 0) index = text.getText().indexOf(query);
        if (index >= 0) { text.selectRange(index, index + query.length()); }
    }
    public int replaceAll(String query, String replacement) {
        if (query == null || query.isEmpty()) return 0;
        String source = text.getText();
        int count = 0, from = 0;
        while ((from = source.indexOf(query, from)) >= 0) { count++; from += query.length(); }
        if (count > 0) text.replaceText(source.replace(query, replacement == null ? "" : replacement));
        return count;
    }
    public boolean gotoLine(int line) {
        if (line < 1) return false;
        int paragraph = Math.min(line - 1, text.getParagraphs().size() - 1);
        if (paragraph < 0) return false;
        text.moveTo(text.getAbsolutePosition(paragraph, 0));
        return true;
    }

    public void saveTo(Path target) {
        path = target;
        dirty = false;
        setText(displayTitle());
    }

    public boolean confirmClose() {
        if (!dirty) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Discard unsaved changes in " + title() + "?",
                ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText(null);
        return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    public String caretStatus() {
        int caret = text.getCaretPosition();
        String beforeCaret = text.getText(0, caret);
        int line = (int) beforeCaret.chars().filter(character -> character == '\n').count() + 1;
        int lastNewline = beforeCaret.lastIndexOf('\n');
        return "Ln " + line + ", Col " + (caret - lastNewline);
    }

    private void applyHighlighting() {
        String extension = path == null ? "" : extension(path);
        text.setStyleSpans(0, SyntaxHighlighter.highlight(text.getText(), extension));
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private void updateCurrentLine() {
        int currentParagraph = text.getCurrentParagraph();
        if (highlightedParagraph != -1 && highlightedParagraph != currentParagraph) {
            text.setParagraphStyle(highlightedParagraph, Collections.emptyList());
        }
        text.setParagraphStyle(currentParagraph, Collections.singleton("current-line"));
        highlightedParagraph = currentParagraph;
    }

    private void refreshLineNumbers() {
        int lines = Math.max(1, text.getText().split("\\R", -1).length);
        lineNumbers.setText(IntStream.rangeClosed(1, lines).mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining("\n")));
    }
}
