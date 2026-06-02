# SuperBlocksDisplays

**Minecraft Plugin (Paper 1.21.x)** to spawn, animate, and manage models from [block-display.com](https://block-display.com) directly on your server.

**Created by Melonzio**

---

## ✨ Features

- 🎭 **Model Spawning** — Import any model from block-display.com using its ID
- 🎬 **Animations** — Play native model animations with full play/stop control
- ⚡ **Speed Control** — Adjust animation playback speed (0.25x to 4x)
- 🔄 **Rotation** — Rotate models to any angle
- 💾 **Persistence** — Models survive server restarts
- 📋 **Interactive List** — Clickable chat interface to manage active models
- 🔍 **Tab Completion** — Full autocomplete support for all commands
- 🔇 **No Console Spam** — Data merge entity messages are automatically silenced

## 📦 Installation

1. Download `SuperBlocksDisplays-1.0.0.jar` from [Releases](https://github.com/Scrulius/SuperBlocksDisplays/releases)
2. Place it in your Paper server's `plugins/` folder
3. Restart the server
4. You're done! Use `/bde help` to see available commands

## 🎮 Commands

| Command | Description |
|---------|------------|
| `/bde spawn <id>` | Spawns a model by its ID |
| `/bde remove [group]` | Removes the specified model or the nearest one |
| `/bde list` | Lists all active models |
| `/bde rotate <yaw> [group]` | Rotates a model (0-360°) |
| `/bde anim <play\|stop> [group]` | Controls model animation |
| `/bde speed <0.25-4.0> [group]` | Sets animation playback speed |
| `/bde info [group]` | Shows detailed information about a model |
| `/bde clearcache` | Clears the downloaded models cache |
| `/bde help` | Displays the help menu |

## 🔑 Permissions

| Permission | Description | Default |
|---------|------------|---------|
| `superblocksdisp.use` | Allows the use of all commands | OP |

## ⚙️ Requirements

- **Paper** 1.21.x
- **Java** 21+

## 📝 Aliases

The main command `/bde` can also be used as `/sbd` and `/blockdisplay`.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.
