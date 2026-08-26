package dev.forgeide.editor;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.ScrollBar;
import javafx.geometry.Orientation;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.application.Platform;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import dev.forgeide.syntax.SyntaxHighlighter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import dev.forgeide.preferences.EditorPreferences;

public final class EditorTab extends Tab {
    private final CodeArea text = new CodeArea();
    private final ScrollBar verticalScroll = new ScrollBar();
    private final PauseTransition highlightDelay = new PauseTransition(Duration.millis(140));
    private final PauseTransition autoSaveDelay = new PauseTransition(Duration.seconds(2));
    private Path path;
    private boolean dirty;
    private boolean initializing = true;
    private int highlightedParagraph = -1;
    private Consumer<EditorTab> caretListener = ignored -> { };
    private Consumer<String> documentChangeListener = ignored -> { };
    private double fontSize = 14;
    private final EditorPreferences preferences = new EditorPreferences();

    public EditorTab(Path path, String content) {
        this.path = path;
        text.replaceText(content);
        text.getStyleClass().add("code-area");
        applyFontSize();
        text.setWrapText(preferences.wrapText());
        text.setParagraphGraphicFactory(index -> createLineNumber(index));
        text.addEventFilter(KeyEvent.KEY_PRESSED, this::handleEditorKey);
        verticalScroll.setOrientation(Orientation.VERTICAL);
        verticalScroll.getStyleClass().add("editor-scrollbar");
        verticalScroll.setMin(0);
        verticalScroll.valueProperty().addListener((obs, old, value) -> text.showParagraphAtTop(value.intValue()));
        text.getVisibleParagraphs().sizeProperty().addListener((obs, old, value) -> syncScrollBar());

        ChangeListener<String> listener = (obs, old, value) -> {
            if (!initializing) {
                dirty = true;
                setText(displayTitle());
                documentChangeListener.accept(value);
                autoSaveDelay.playFromStart();
            }
            syncScrollBar();
            highlightDelay.playFromStart();
        };
        text.textProperty().addListener(listener);
        text.caretPositionProperty().addListener((obs, old, value) -> {
            updateCurrentLine();
            caretListener.accept(this);
        });
        highlightDelay.setOnFinished(event -> applyHighlighting());
        autoSaveDelay.setOnFinished(event -> autoSave());
        updateCurrentLine();
        applyHighlighting();
        initializing = false;
        verticalScroll.setMax(Math.max(0, text.getParagraphs().size() - 1));

        HBox contentBox = new HBox(text, verticalScroll);
        HBox.setHgrow(text, Priority.ALWAYS);
        setContent(contentBox);
        setText(title());
    }

    public Path getPath() { return path; }
    public String getDocumentText() { return text.getText(); }
    public String title() { return path == null ? "Untitled" : path.getFileName().toString(); }
    public boolean isDirty() { return dirty; }
    public String displayTitle() { return title() + (dirty ? " ●" : ""); }
    public void setCaretListener(Consumer<EditorTab> listener) { caretListener = listener == null ? ignored -> { } : listener; }
    public void setDocumentChangeListener(Consumer<String> listener) { documentChangeListener = listener == null ? ignored -> { } : listener; }
    public void setWrap(boolean enabled) { text.setWrapText(enabled); }
    public void applyPreferences() { text.setWrapText(preferences.wrapText()); }
    public void undo() { text.undo(); }
    public void redo() { text.redo(); }
    public void focusEditor() { text.requestFocus(); }
    public void increaseFont() { fontSize = Math.min(32, fontSize + 1); applyFontSize(); }
    public void decreaseFont() { fontSize = Math.max(8, fontSize - 1); applyFontSize(); }
    public void resetFont() { fontSize = 14; applyFontSize(); }
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
    public int caretPosition() { return text.getCaretPosition(); }
    public void gotoPosition(int position) { text.moveTo(Math.max(0, Math.min(position, text.getLength()))); }

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

    private void applyFontSize() {
        text.setStyle("-fx-font-family: 'JetBrains Mono'; -fx-font-size: " + fontSize + "px;");
    }

    private void handleEditorKey(KeyEvent event) {
        if (event.getCode() == KeyCode.TAB && preferences.insertSpaces()) {
            text.replaceSelection(" ".repeat(preferences.tabSize())); event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
            int caret = text.getCaretPosition(); String before = text.getText(0, caret);
            String line = before.substring(before.lastIndexOf('\n') + 1);
            String indent = line.substring(0, line.length() - line.stripLeading().length());
            if (line.trim().endsWith("{") || line.trim().endsWith(":")) indent += " ".repeat(preferences.tabSize());
            final String indentation = indent;
            if (!indentation.isEmpty()) Platform.runLater(() -> text.insertText(text.getCaretPosition(), indentation));
        }
    }

    private void autoSave() {
        if (!dirty || path == null) return;
        try { java.nio.file.Files.writeString(path, text.getText()); dirty = false; setText(displayTitle()); }
        catch (java.io.IOException ignored) { }
    }

    private void syncScrollBar() {
        int total = text.getParagraphs().size();
        int visible = Math.max(1, text.getVisibleParagraphs().size());
        verticalScroll.setMax(Math.max(0, total - visible));
        verticalScroll.setVisibleAmount(visible);
        int first = total == 0 ? 0 : text.visibleParToAllParIndex(0);
        if (Math.abs(verticalScroll.getValue() - first) > 0.5) verticalScroll.setValue(first);
    }

    private javafx.scene.control.Label createLineNumber(int index) {
        javafx.scene.control.Label lineNumber = new javafx.scene.control.Label();
        lineNumber.getStyleClass().add("line-number");
        lineNumber.setAlignment(Pos.CENTER_LEFT);
        lineNumber.textProperty().bind(Bindings.createStringBinding(() -> {
            int digits = Integer.toString(Math.max(1, text.getParagraphs().size())).length();
            return String.format("%" + digits + "d", index + 1);
        }, text.getParagraphs().sizeProperty()));
        return lineNumber;
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

}
