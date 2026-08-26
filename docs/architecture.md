# ForgeIDE architecture

ForgeIDE is split by responsibility: the editor owns document state, the explorer owns workspace navigation, the index provides fast symbol lookup, and the LSP layer manages language-server processes.

The UI composes these services without embedding parsing or persistence logic in controls.
