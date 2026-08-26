package dev.forgeide.lsp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/** Minimal stdio transport. JSON-RPC messaging is intentionally kept behind this boundary. */
final class LanguageServerProcess implements AutoCloseable {
    private final Process process;

    private LanguageServerProcess(Process process) { this.process = process; }

    static LanguageServerProcess start(String command, Path workspace) throws IOException {
        String[] parts = command.split("\\s+");
        ProcessBuilder builder = new ProcessBuilder(Arrays.asList(parts));
        builder.directory(workspace.toFile());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return new LanguageServerProcess(builder.start());
    }

    boolean isRunning() { return process.isAlive(); }

    @Override public void close() { process.destroy(); }
}
