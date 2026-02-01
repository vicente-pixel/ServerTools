# Server Tools

A Fabric mod for Minecraft 1.21.x that provides a collection of tools for capturing and downloading server data.

**Use at your own risk!**

## Features

- **Chunk Download (F9)** - Download terrain chunks for offline play
- **Menu Capture (J)** - Capture container GUIs with items, lore, and textures
- **NPC Capture (B)** - Capture NPC skins, holograms, and equipment
- **Sound Capture (K/N)** - Record playing sounds and sequences
- **Map Capture (H)** - Capture map art pixel data
- **Head Capture (G)** - Scan for armor stands with player heads
  - **Fairy Soul Capture (P)** - Scan for fairy souls in the SkyBlockmake it  makmak hub only 
- **Chat Copy (Ctrl/Cmd+C)** - Copy chat messages with color formatting

## Requirements

- Minecraft 1.21.x
- Fabric Loader 0.18.2+
- Fabric API

## Installation

1. Install Fabric Loader for Minecraft 1.21.x
2. Install Fabric API
3. Place `servertools-1.0.0.jar` in `.minecraft/mods/`

## Keybinds

| Key | Action |
|-----|--------|
| F9 | Toggle chunk download |
| J | Capture menu |
| B | Capture NPC |
| K | Toggle sound capture |
| N | Toggle sequence capture |
| H | Capture map |
| G | Toggle head capture |
| P | Toggle armor stand capture |

## Building

```bash
./gradlew build
```

Output: `build/libs/servertools-1.0.0.jar`

## License

CC0-1.0 (Public Domain)
