package dev.forgeide.lsp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Small LSP JSON-RPC 2.0 transport using Content-Length framed stdio messages. */
final class JsonRpcClient implements AutoCloseable {
    private final InputStream input;
    private final OutputStream output;
    private final AtomicInteger ids = new AtomicInteger();
    private final Consumer<JsonObject> notifications;
    private final Map<Integer, Consumer<JsonObject>> responses = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    JsonRpcClient(InputStream input, OutputStream output, Consumer<JsonObject> notifications) {
        this.input = input; this.output = output; this.notifications = notifications;
        Thread reader = new Thread(this::readLoop, "forgeide-lsp-reader");
        reader.setDaemon(true); reader.start();
    }

    synchronized int request(String method, JsonObject params) throws IOException {
        int id = ids.incrementAndGet();
        JsonObject message = base(method, params); message.addProperty("id", id); write(message);
        return id;
    }

    synchronized void request(String method, JsonObject params, Consumer<JsonObject> callback) throws IOException {
        int id = ids.incrementAndGet(); responses.put(id, callback);
        JsonObject message = base(method, params); message.addProperty("id", id); write(message);
    }

    synchronized void notify(String method, JsonObject params) throws IOException { write(base(method, params)); }

    private JsonObject base(String method, JsonObject params) {
        JsonObject message = new JsonObject(); message.addProperty("jsonrpc", "2.0"); message.addProperty("method", method);
        if (params != null) message.add("params", params); return message;
    }

    private void write(JsonObject message) throws IOException {
        byte[] bytes = message.toString().getBytes(StandardCharsets.UTF_8);
        output.write(("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(bytes); output.flush();
    }

    private void readLoop() {
        try {
            while (running) {
                String line = readLine(); if (line == null) break;
                if (!line.toLowerCase().startsWith("content-length:")) continue;
                int length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                if (readLine() == null) break;
                byte[] body = input.readNBytes(length); if (body.length != length) break;
                JsonObject message = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                Consumer<JsonObject> callback = message.has("id") ? responses.remove(message.get("id").getAsInt()) : null;
                if (callback != null) callback.accept(message);
                else if (message.has("method")) notifications.accept(message);
            }
        } catch (Exception ignored) { }
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(); int value;
        while ((value = input.read()) != -1) { if (value == '\n') break; if (value != '\r') line.write(value); }
        return value == -1 && line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
    }

    @Override public void close() { running = false; }
}
