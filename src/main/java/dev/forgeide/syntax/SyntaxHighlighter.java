package dev.forgeide.syntax;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies the first syntax-highlighting rules for common C-style languages. */
public final class SyntaxHighlighter {
    private static final Pattern TOKENS = Pattern.compile(
            "(?<COMMENT>//[^\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\n]*)"
                    + "|(?<STRING>\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*')"
                    + "|(?<KEYWORD>\\b(?:abstract|as|assert|async|await|break|case|catch|class|const|continue|def|default|else|extends|final|finally|for|from|function|if|implements|import|in|interface|let|new|null|package|private|protected|public|return|static|super|switch|this|throw|try|var|void|while|with|yield)\\b)"
                    + "|(?<NUMBER>\\b(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b)"
                    + "|(?<ANNOTATION>@[A-Za-z_][A-Za-z0-9_]*)");

    private SyntaxHighlighter() { }

    public static StyleSpans<Collection<String>> highlight(String source) {
        Matcher matcher = TOKENS.matcher(source);
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        int lastEnd = 0;
        while (matcher.find()) {
            spans.add(Collections.emptyList(), matcher.start() - lastEnd);
            String style = matcher.group("COMMENT") != null ? "comment"
                    : matcher.group("STRING") != null ? "string"
                    : matcher.group("KEYWORD") != null ? "keyword"
                    : matcher.group("NUMBER") != null ? "number" : "annotation";
            spans.add(Collections.singleton(style), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spans.add(Collections.emptyList(), source.length() - lastEnd);
        return spans.create();
    }
}
