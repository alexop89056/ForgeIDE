package dev.forgeide.preferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

/** Persistent desktop preferences, similar in purpose to browser localStorage. */
public final class WorkspacePreferences {
    private static final String LAST_WORKSPACE = "lastWorkspace";
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
}
