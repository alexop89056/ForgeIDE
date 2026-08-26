package dev.forgeide.lsp;

import com.google.gson.JsonObject;
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

    @Override public void close() { rpc.close(); process.destroy(); }
}
