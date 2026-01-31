# World Downloader

A Fabric mod for Minecraft 1.21.x that allows you to download chunks from multiplayer servers for offline play.

## Features

- Download terrain chunks from multiplayer servers
- Capture block entities (chests, furnaces, signs, etc.) with their contents
- Save entities (mobs, animals, items) in downloaded chunks
- Support for all dimensions (Overworld, Nether, The End)
- Simple toggle-based operation with keybind

## Requirements

- Minecraft 1.21.x
- Fabric Loader 0.18.2 or higher
- Fabric API

## Installation

1. Install Fabric Loader for Minecraft 1.21.x
2. Download and install Fabric API
3. Place `worlddownloader-1.0.0.jar` in your `.minecraft/mods` folder
4. Launch Minecraft with the Fabric profile

## Usage

1. Join a multiplayer server
2. Wait for chunks to load around you
3. Press **F9** to start downloading
4. Walk around to capture more chunks as they load
5. Press **F9** again to stop downloading

Downloaded worlds are saved to `.minecraft/saves/WDL_<server>_<timestamp>/`

## What Gets Saved

- Terrain and block data
- Block entities (chests, barrels, furnaces, signs, banners, etc.)
- Container inventories
- Entities (mobs, animals, dropped items, minecarts, etc.)
- Multiple dimensions

## Keybinds

| Key | Action |
|-----|--------|
| F9  | Toggle world download on/off |

## Notes

- Only chunks that are loaded on your client can be downloaded
- The mod captures data as your client receives it from the server
- Downloaded worlds use a flat world generator for unexplored areas
- Player entities are not saved

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## License

CC0-1.0
