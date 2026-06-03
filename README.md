# SuperBlocksDisplays

**Minecraft Plugin (Paper 1.21.x)** to spawn, animate, and manage models from [block-display.com](https://block-display.com) directly on your server.

**Created by Melonzio**

---

## ✨ Features

- 🎭 **Model Spawning** — Import any model from block-display.com using its ID with a custom name
- 📚 **Model Library** — Download models permanently and spawn them anytime without needing the API ID again
- 🎬 **Animations** — Play native model animations with loop/once modes
- ⚡ **Speed Control** — Adjust animation playback speed (0.25x to 4x)
- 🔄 **Rotation** — Rotate models to any angle
- 💾 **Persistence** — Models survive server restarts without duplication
- 📛 **Custom Names** — Every model gets a mandatory name for easy management
- 🔍 **Smart Targeting** — Reference models by name, `nearest`, or partial UUID
- 📍 **Teleport** — Teleport directly to any spawned model
- 🧹 **Purge Command** — Kill all display entities in a radius for quick cleanup
- 🔇 **Silent Operation** — All command feedback (console & in-game) is automatically silenced
- 🔍 **Tab Completion** — Full autocomplete with model and library name suggestions

## 📦 Installation

1. Download `SuperBlocksDisplays-1.0.0.jar` from [Releases](https://github.com/Scrulius/SuperBlocksDisplays/releases)
2. Place it in your Paper server's `plugins/` folder
3. Restart the server
4. Use `/bde help` to see available commands

## 🎮 Commands

### Model Management

| Command | Description |
|---------|------------|
| `/bde spawn <id\|lib> <name>` | Spawns a model (from API ID or library name) |
| `/bde remove [name\|nearest]` | Removes a model by name or the nearest one |
| `/bde tp [name\|nearest]` | Teleport to a model's location |
| `/bde list` | Lists all active models with their names |
| `/bde info [name\|nearest]` | Shows detailed model information |
| `/bde purge <1-10>` | Kills all display entities within radius |

### Animation

| Command | Description |
|---------|------------|
| `/bde anim play <loop\|once> [name]` | Starts animation playback |
| `/bde anim stop [name\|nearest]` | Stops animation playback |
| `/bde speed <0.25-4.0> [name]` | Sets animation playback speed |
| `/bde rotate <yaw> [name\|nearest]` | Rotates a model (0-360°) |

### Library

| Command | Description |
|---------|------------|
| `/bde download <model_id> <name>` | Downloads a model and saves it permanently |
| `/bde library` | Lists all saved models (clickable UI) |
| `/bde undownload <name>` | Removes a model from the library |
| `/bde clearcache` | Clears the API cache (not the library) |

> **Tip:** If no model is specified, commands like `remove`, `tp`, `rotate`, `info`, etc. will target the **nearest** model within 15 blocks.

> **Library vs Cache:** The library (`/bde download`) saves models permanently in `plugins/SuperBlocksDisplays/library/`. The cache is temporary and can be cleared with `/bde clearcache`. Library models are never deleted unless you use `/bde undownload`.

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
