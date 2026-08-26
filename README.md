# ForgeIDE

A lightweight desktop text editor built with JavaFX. ForgeIDE is a focused foundation for a portfolio-grade IDE: fast startup, a distraction-free dark interface and a clean path toward syntax highlighting, project navigation and extensions.

## Current features

- Multiple editor tabs
- VS Code-style file explorer with workspace folder selection
- Workspace refresh and one-click new-file creation
- New, open, save and save-as workflows
- Line numbers
- Undo and redo
- Optional word wrapping
- Basic syntax highlighting with language-aware colors for Java, JavaScript, Python and JSON
- Unsaved-change indicator and close confirmation
- Current-line highlighting and cursor position (`Ln`, `Col`)
- Find, replace-all and go-to-line dialogs
- Quick Open (`Ctrl/Cmd+P`) and recently opened file tracking
- Keyboard shortcuts: `Ctrl/Cmd+N`, `Ctrl/Cmd+O`, `Ctrl/Cmd+S`, `Ctrl/Cmd+W`, `Ctrl/Cmd+F`, `Ctrl/Cmd+H`, `Ctrl/Cmd+G`

## Tech stack

- Java 21
- JavaFX 21
- RichTextFX `CodeArea`

## Architecture

The application is split by feature and responsibility:

```text
dev.forgeide
├── ForgeIdeApplication.java      # JavaFX entry point
├── ui/ForgeIdeWindow.java        # window composition and menus
├── editor/
│   ├── EditorTabs.java            # tab lifecycle and file operations
│   └── EditorTab.java             # one document, line numbers and editor state
├── explorer/FileExplorer.java     # workspace tree and file navigation
└── syntax/SyntaxHighlighter.java  # language-agnostic token styling rules
```

The UI layer coordinates feature components, while editor state, file navigation and syntax highlighting remain isolated and independently replaceable.
- Gradle

## Run

```bash
./gradlew run
```

The Gradle JavaFX plugin downloads the platform-specific JavaFX modules automatically. A JDK 21 installation is required.

## Roadmap

- Syntax highlighting for Java, JavaScript and Python
- File-system/project explorer
- Find and replace
- Recent projects and session restore
- Theme and editor preferences
