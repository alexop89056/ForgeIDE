package dev.forgeide.preferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;

/** Persistent desktop preferences, similar in purpose to browser localStorage. */
public final class WorkspacePreferences {
    private static final String LAST_WORKSPACE = "lastWorkspace";
    private static final String OPEN_FILES = "openFiles";
    private final Preferences preferences = Preferences.userNodeForPackage(WorkspacePreferences.class);

    public Optional<Path> lastWorkspace() {
        String value = preferences.get(LAST_WORKSPACE, "");
        if (value.isBlank()) return Optional.empty();
        Path path = Path.of(value);
        return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
    }

    public void rememberWorkspace(Path workspace) {
        if (workspace != null && Files.isDirectory(workspace)) preferences.put(LAST_WORKSPACE, workspace.toAbsolutePath().normalize().toString());
    }

    public List<Path> openFiles() {
        return Arrays.stream(preferences.get(OPEN_FILES, "").split("\\R"))
                .filter(value -> !value.isBlank()).map(Path::of).filter(Files::isRegularFile).collect(Collectors.toList());
    }

    public void rememberOpenFiles(List<Path> files) {
        preferences.put(OPEN_FILES, files.stream().map(path -> path.toAbsolutePath().normalize().toString()).collect(Collectors.joining("\n")));
    }
}
