package dev.forgeide.lsp;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/** Minimal stdio transport. JSON-RPC messaging is intentionally kept behind this boundary. */
final class LanguageServerProcess implements AutoCloseable {
    private final Process process;
    private final JsonRpcClient rpc;

    private LanguageServerProcess(Process process) { this.process = process; this.rpc = new JsonRpcClient(process.getInputStream(), process.getOutputStream(), ignored -> { }); }

    static LanguageServerProcess start(String command, Path workspace) throws IOException {
        String[] parts = command.split("\\s+");
        ProcessBuilder builder = new ProcessBuilder(Arrays.asList(parts));
        builder.directory(workspace.toFile());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        LanguageServerProcess server = new LanguageServerProcess(builder.start());
        JsonObject params = new JsonObject();
        params.addProperty("processId", ProcessHandle.current().pid());
        params.addProperty("rootUri", workspace.toUri().toString());
        params.add("capabilities", new JsonObject());
        server.rpc.request("initialize", params);
        server.rpc.notify("initialized", new JsonObject());
        return server;
    }

    boolean isRunning() { return process.isAlive(); }

    void didOpen(Path path, String languageId, String text) throws IOException {
        JsonObject document = new JsonObject(); document.addProperty("uri", path.toUri().toString());
        document.addProperty("languageId", languageId); document.addProperty("version", 1); document.addProperty("text", text);
        JsonObject params = new JsonObject(); params.add("textDocument", document); rpc.notify("textDocument/didOpen", params);
    }

    void didChange(Path path, int version, String text) throws IOException {
        JsonObject document = new JsonObject(); document.addProperty("uri", path.toUri().toString()); document.addProperty("version", version);
        JsonObject change = new JsonObject(); change.addProperty("text", text);
        com.google.gson.JsonArray changes = new com.google.gson.JsonArray(); changes.add(change);
        JsonObject params = new JsonObject(); params.add("textDocument", document); params.add("contentChanges", changes);
        rpc.notify("textDocument/didChange", params);
    }

    @Override public void close() { rpc.close(); process.destroy(); }
}
