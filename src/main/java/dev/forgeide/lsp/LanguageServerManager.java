package dev.forgeide.lsp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import com.google.gson.JsonObject;

/** Discovers and owns optional language-server processes for the current workspace. */
public final class LanguageServerManager implements AutoCloseable {
    private static final Map<String, String> COMMANDS = Map.of(
            "java", "jdtls",
            "js", "typescript-language-server --stdio",
            "jsx", "typescript-language-server --stdio",
            "ts", "typescript-language-server --stdio",
            "py", "pyright-langserver --stdio",
            "python", "pyright-langserver --stdio");

    private LanguageServerProcess process;
    private int documentVersion;

    public Optional<String> supportedLanguage(String extension) {
        String key = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return COMMANDS.containsKey(key) ? Optional.of(key) : Optional.empty();
    }

    public boolean start(String extension, Path workspace) {
        stop();
        Optional<String> language = supportedLanguage(extension);
        if (language.isEmpty()) return false;
        try {
            process = LanguageServerProcess.start(COMMANDS.get(language.get()), workspace);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public boolean isRunning() { return process != null && process.isRunning(); }

    public void didOpen(Path path, String languageId, String text) {
        if (process == null) return;
        try { documentVersion = 1; process.didOpen(path, languageId, text); } catch (IOException ignored) { }
    }

    public void didChange(Path path, String text) {
        if (process == null) return;
        try { process.didChange(path, ++documentVersion, text); } catch (IOException ignored) { }
    }

    public void completion(Path path, int line, int character, Consumer<JsonObject> callback) {
        if (process == null) return;
        try { process.completion(path, line, character, callback); } catch (IOException ignored) { }
    }

    public void stop() {
        if (process != null) process.close();
        process = null;
    }

    @Override public void close() { stop(); }
}
