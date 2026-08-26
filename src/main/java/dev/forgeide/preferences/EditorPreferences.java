package dev.forgeide.preferences;

import java.util.prefs.Preferences;

public final class EditorPreferences {
    private final Preferences preferences = Preferences.userNodeForPackage(EditorPreferences.class);
    public int tabSize() { return preferences.getInt("tabSize", 4); }
    public boolean insertSpaces() { return preferences.getBoolean("insertSpaces", true); }
    public boolean wrapText() { return preferences.getBoolean("wrapText", false); }
    public String theme() { return preferences.get("theme", "dark"); }
    public void setTabSize(int value) { preferences.putInt("tabSize", Math.max(1, Math.min(8, value))); }
    public void setInsertSpaces(boolean value) { preferences.putBoolean("insertSpaces", value); }
    public void setWrapText(boolean value) { preferences.putBoolean("wrapText", value); }
    public void setTheme(String value) { preferences.put("theme", value); }
}
