package dev.forgeide.index;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight presentation index for common Java, JS/TS and Python declarations. */
public final class SymbolIndex {
    private static final Pattern DECLARATION = Pattern.compile("\\b(class|interface|enum|def|function)\\s+([A-Za-z_$][\\w$]*)");
    private final List<SymbolLocation> symbols = new CopyOnWriteArrayList<>();

    public void indexWorkspace(Path root, Consumer<String> progress) {
        symbols.clear();
        if (root == null || !Files.isDirectory(root)) return;
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile).filter(this::supported).filter(path -> !path.toString().contains("/.git/"))
                    .forEach(path -> indexFile(path, progress));
        } catch (IOException ignored) { }
    }

    private void indexFile(Path file, Consumer<String> progress) {
        try {
            String source = Files.readString(file); Matcher matcher = DECLARATION.matcher(source);
            while (matcher.find()) {
                int line = (int) source.substring(0, matcher.start()).chars().filter(c -> c == '\n').count() + 1;
                symbols.add(new SymbolLocation(matcher.group(2), matcher.group(1), file, line));
            }
            progress.accept("Indexed " + file.getFileName());
        } catch (IOException ignored) { }
    }

    public List<SymbolLocation> search(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return symbols.stream().filter(symbol -> symbol.name().toLowerCase(Locale.ROOT).contains(needle)).limit(100).toList();
    }

    public int size() { return symbols.size(); }
    private boolean supported(Path path) { String n = path.getFileName().toString().toLowerCase(Locale.ROOT); return n.endsWith(".java") || n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".py"); }
}
