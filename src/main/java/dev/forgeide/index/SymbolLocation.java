package dev.forgeide.index;

import java.nio.file.Path;

public record SymbolLocation(String name, String kind, Path file, int line) {
    @Override public String toString() { return name + "  ·  " + kind + "  ·  " + file.getFileName() + ":" + line; }
}
