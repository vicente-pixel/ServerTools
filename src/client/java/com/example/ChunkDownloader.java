package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkDownloader {
    public static final Logger LOGGER = LoggerFactory.getLogger("ChunkDownloader");

    private static ChunkDownloader instance;

    private boolean downloading = false;
    private Path saveDirectory;
    private String worldName;
    private final Set<ChunkPos> savedChunks = ConcurrentHashMap.newKeySet();
    private final Map<String, RegionFileWriter> regionFiles = new ConcurrentHashMap<>();
    private final Map<String, RegionFileWriter> entityRegionFiles = new ConcurrentHashMap<>();
    private int chunksDownloaded = 0;
    private int entitiesSaved = 0;

    public static ChunkDownloader getInstance() {
        if (instance == null) {
            instance = new ChunkDownloader();
        }
        return instance;
    }

    public boolean isDownloading() {
        return downloading;
    }

    public void toggleDownload() {
        if (downloading) {
            stopDownload();
        } else {
            startDownload();
        }
    }

    public void startDownload() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) {
            sendMessage("Must be in a world to download!");
            return;
        }

        String serverName = getServerName();
        worldName = "ST_" + serverName + "_" + System.currentTimeMillis();
        saveDirectory = mc.gameDirectory.toPath().resolve("saves").resolve(worldName);

        try {
            Files.createDirectories(saveDirectory);
            Files.createDirectories(saveDirectory.resolve("region"));
            Files.createDirectories(saveDirectory.resolve("entities"));
            Files.createDirectories(saveDirectory.resolve("DIM-1").resolve("region"));
            Files.createDirectories(saveDirectory.resolve("DIM-1").resolve("entities"));
            Files.createDirectories(saveDirectory.resolve("DIM1").resolve("region"));
            Files.createDirectories(saveDirectory.resolve("DIM1").resolve("entities"));
            Files.createDirectories(saveDirectory.resolve("data"));

            savedChunks.clear();
            chunksDownloaded = 0;
            downloading = true;

            saveLevelData();

            // Save all currently loaded chunks
            saveAllLoadedChunks();

            sendMessage("Started downloading world to: " + worldName);
            sendMessage("Walk around to download more chunks. Press F9 to stop.");
        } catch (IOException e) {
            LOGGER.error("Failed to start download", e);
            sendMessage("Failed to start download: " + e.getMessage());
        }
    }

    public void stopDownload() {
        if (!downloading) return;

        downloading = false;

        try {
            saveLevelData();
        } catch (IOException e) {
            LOGGER.error("Failed to save level data on stop", e);
        }

        closeRegionFiles();

        sendMessage("Stopped downloading. Saved " + chunksDownloaded + " chunks and " + entitiesSaved + " entities to: " + worldName);
    }

    private String getServerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            return sanitizeFileName(mc.getCurrentServer().ip);
        }
        return "unknown_server";
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private void saveAllLoadedChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int saved = 0;
        var chunkSource = mc.level.getChunkSource();

        // Get the render distance to determine which chunks to save
        int renderDistance = mc.options.renderDistance().get();
        if (mc.player == null) return;

        int playerChunkX = mc.player.chunkPosition().x;
        int playerChunkZ = mc.player.chunkPosition().z;

        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                try {
                    LevelChunk chunk = chunkSource.getChunk(chunkX, chunkZ, false);
                    if (chunk != null) {
                        saveChunk(chunk);
                        saved++;
                    }
                } catch (Exception e) {
                    // Chunk not loaded, skip
                }
            }
        }

        sendMessage("Saved " + saved + " already loaded chunks.");
    }

    public void saveChunk(LevelChunk chunk) {
        if (!downloading || chunk == null) return;

        ChunkPos pos = chunk.getPos();
        if (savedChunks.contains(pos)) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;

            CompoundTag chunkTag = serializeChunk(level, chunk);

            Path regionDir = getRegionDirectory(level);
            Files.createDirectories(regionDir);

            int regionX = pos.getRegionX();
            int regionZ = pos.getRegionZ();
            String regionKey = regionDir.toString() + "/r." + regionX + "." + regionZ + ".mca";

            RegionFileWriter regionFile = regionFiles.computeIfAbsent(regionKey, k -> {
                try {
                    Path regionPath = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
                    return new RegionFileWriter(regionPath);
                } catch (IOException e) {
                    LOGGER.error("Failed to open region file", e);
                    return null;
                }
            });

            if (regionFile != null) {
                regionFile.writeChunk(pos, chunkTag);
                savedChunks.add(pos);
                chunksDownloaded++;

                // Also save entities to separate entities region file
                saveEntitiesForChunk(level, chunk);

                if (chunksDownloaded % 50 == 0) {
                    sendMessage("Downloaded " + chunksDownloaded + " chunks...");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save chunk at {}", pos, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CompoundTag serializeBlockState(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

        if (!state.getValues().isEmpty()) {
            CompoundTag properties = new CompoundTag();
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                Property property = entry.getKey();
                Comparable value = entry.getValue();
                properties.putString(property.getName(), property.getName(value));
            }
            tag.put("Properties", properties);
        }

        return tag;
    }

    private CompoundTag serializeChunk(ClientLevel level, LevelChunk chunk) {
        CompoundTag tag = new CompoundTag();
        ChunkPos pos = chunk.getPos();

        tag.putInt("DataVersion", 4189);
        tag.putInt("xPos", pos.x);
        tag.putInt("zPos", pos.z);
        tag.putInt("yPos", chunk.getMinY() >> 4);
        tag.putString("Status", "minecraft:full");
        tag.putLong("LastUpdate", level.getGameTime());
        tag.putLong("InhabitedTime", 0L);

        ListTag sectionsTag = new ListTag();
        LevelChunkSection[] sections = chunk.getSections();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null) continue;

            CompoundTag sectionTag = new CompoundTag();
            int sectionY = chunk.getSectionYFromSectionIndex(i);
            sectionTag.putByte("Y", (byte) sectionY);

            CompoundTag blockStatesTag = new CompoundTag();
            ListTag palette = new ListTag();

            PalettedContainer<BlockState> states = section.getStates();

            Map<BlockState, Integer> paletteMap = new HashMap<>();
            int[] blockIndices = new int[4096];
            int idx = 0;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = states.get(x, y, z);
                        int paletteIdx = paletteMap.computeIfAbsent(state, s -> {
                            palette.add(serializeBlockState(s));
                            return paletteMap.size();
                        });
                        blockIndices[idx++] = paletteIdx;
                    }
                }
            }

            blockStatesTag.put("palette", palette);

            if (paletteMap.size() > 1) {
                int bitsPerEntry = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteMap.size() - 1));
                int entriesPerLong = 64 / bitsPerEntry;
                int numLongs = (4096 + entriesPerLong - 1) / entriesPerLong;
                long[] data = new long[numLongs];
                long mask = (1L << bitsPerEntry) - 1;

                for (int j = 0; j < 4096; j++) {
                    int longIdx = j / entriesPerLong;
                    int bitOffset = (j % entriesPerLong) * bitsPerEntry;
                    data[longIdx] |= ((long) blockIndices[j] & mask) << bitOffset;
                }

                blockStatesTag.putLongArray("data", data);
            }

            sectionTag.put("block_states", blockStatesTag);

            CompoundTag biomesTag = new CompoundTag();
            ListTag biomePalette = new ListTag();
            biomePalette.add(StringTag.valueOf("minecraft:plains"));
            biomesTag.put("palette", biomePalette);
            sectionTag.put("biomes", biomesTag);

            sectionsTag.add(sectionTag);
        }

        tag.put("sections", sectionsTag);

        ListTag blockEntitiesTag = new ListTag();
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockEntity be = entry.getValue();
            try {
                CompoundTag beTag = be.saveWithFullMetadata(level.registryAccess());
                blockEntitiesTag.add(beTag);
            } catch (Exception e) {
                LOGGER.warn("Failed to save block entity at {}", entry.getKey(), e);
            }
        }
        tag.put("block_entities", blockEntitiesTag);

        CompoundTag heightmaps = new CompoundTag();
        tag.put("Heightmaps", heightmaps);

        return tag;
    }

    private Path getRegionDirectory(ClientLevel level) {
        String dimensionPath = level.dimension().toString();

        if (dimensionPath.contains("overworld")) {
            return saveDirectory.resolve("region");
        } else if (dimensionPath.contains("the_nether")) {
            return saveDirectory.resolve("DIM-1").resolve("region");
        } else if (dimensionPath.contains("the_end")) {
            return saveDirectory.resolve("DIM1").resolve("region");
        } else {
            String safeName = sanitizeFileName(dimensionPath);
            return saveDirectory.resolve("dimensions").resolve(safeName).resolve("region");
        }
    }

    private Path getEntitiesDirectory(ClientLevel level) {
        String dimensionPath = level.dimension().toString();

        if (dimensionPath.contains("overworld")) {
            return saveDirectory.resolve("entities");
        } else if (dimensionPath.contains("the_nether")) {
            return saveDirectory.resolve("DIM-1").resolve("entities");
        } else if (dimensionPath.contains("the_end")) {
            return saveDirectory.resolve("DIM1").resolve("entities");
        } else {
            String safeName = sanitizeFileName(dimensionPath);
            return saveDirectory.resolve("dimensions").resolve(safeName).resolve("entities");
        }
    }

    private void saveEntitiesForChunk(ClientLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();

        try {
            Path entitiesDir = getEntitiesDirectory(level);
            Files.createDirectories(entitiesDir);

            int regionX = pos.getRegionX();
            int regionZ = pos.getRegionZ();
            String regionKey = entitiesDir.toString() + "/r." + regionX + "." + regionZ + ".mca";

            RegionFileWriter entityFile = entityRegionFiles.computeIfAbsent(regionKey, k -> {
                try {
                    Path regionPath = entitiesDir.resolve("r." + regionX + "." + regionZ + ".mca");
                    return new RegionFileWriter(regionPath);
                } catch (IOException e) {
                    LOGGER.error("Failed to open entity region file", e);
                    return null;
                }
            });

            if (entityFile != null) {
                CompoundTag entityChunkTag = new CompoundTag();
                entityChunkTag.putInt("DataVersion", 4189);
                entityChunkTag.putIntArray("Position", new int[]{pos.x, pos.z});

                ListTag entitiesTag = new ListTag();
                AABB chunkBounds = new AABB(
                        pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(),
                        pos.getMaxBlockX() + 1, level.getMaxY(), pos.getMaxBlockZ() + 1
                );

                int entityCount = 0;
                for (Entity entity : level.getEntities((Entity) null, chunkBounds, e -> !(e instanceof Player))) {
                    try {
                        CompoundTag entityTag = new CompoundTag();
                        entityTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                        entityTag.putDouble("x", entity.getX());
                        entityTag.putDouble("y", entity.getY());
                        entityTag.putDouble("z", entity.getZ());
                        entityTag.putFloat("yRot", entity.getYRot());
                        entityTag.putFloat("xRot", entity.getXRot());
                        entityTag.putString("UUID", entity.getUUID().toString());
                        entitiesTag.add(entityTag);
                        entityCount++;
                    } catch (Exception e) {
                        LOGGER.warn("Failed to save entity {}", entity.getType(), e);
                    }
                }
                entityChunkTag.put("Entities", entitiesTag);
                entitiesSaved += entityCount;

                entityFile.writeChunk(pos, entityChunkTag);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save entities for chunk {}", pos, e);
        }
    }

    private void saveLevelData() throws IOException {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        CompoundTag root = new CompoundTag();
        CompoundTag data = new CompoundTag();

        data.putString("LevelName", worldName);
        data.putInt("version", 19133);
        data.putInt("DataVersion", 4189);
        data.putBoolean("allowCommands", true);
        data.putInt("GameType", 1);
        data.putBoolean("hardcore", false);
        data.putLong("Time", level.getGameTime());
        data.putLong("DayTime", level.getDayTime());
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putInt("clearWeatherTime", 0);
        data.putInt("rainTime", 0);
        data.putBoolean("raining", false);
        data.putInt("thunderTime", 0);
        data.putBoolean("thundering", false);
        data.putFloat("SpawnAngle", 0.0f);
        data.putBoolean("initialized", true);
        data.putInt("WanderingTraderSpawnChance", 25);
        data.putInt("WanderingTraderSpawnDelay", 24000);
        data.putBoolean("WasModded", true);
        data.putBoolean("Difficulty", false);
        data.putByte("Difficulty", (byte) 2);
        data.putBoolean("DifficultyLocked", false);

        if (mc.player != null) {
            BlockPos playerPos = mc.player.blockPosition();
            data.putInt("SpawnX", playerPos.getX());
            data.putInt("SpawnY", playerPos.getY());
            data.putInt("SpawnZ", playerPos.getZ());
        } else {
            data.putInt("SpawnX", 0);
            data.putInt("SpawnY", 64);
            data.putInt("SpawnZ", 0);
        }

        CompoundTag worldGenSettings = new CompoundTag();
        worldGenSettings.putLong("seed", 0L);
        worldGenSettings.putBoolean("generate_features", true);
        worldGenSettings.putBoolean("bonus_chest", false);

        CompoundTag dimensions = new CompoundTag();

        CompoundTag overworld = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        CompoundTag overworldGen = new CompoundTag();
        overworldGen.putString("type", "minecraft:flat");
        CompoundTag overworldFlatSettings = new CompoundTag();
        overworldFlatSettings.put("layers", new ListTag());
        overworldFlatSettings.putString("biome", "minecraft:the_void");
        overworldFlatSettings.putBoolean("features", false);
        overworldFlatSettings.putBoolean("lakes", false);
        overworldFlatSettings.put("structure_overrides", new ListTag());
        overworldGen.put("settings", overworldFlatSettings);
        overworld.put("generator", overworldGen);
        dimensions.put("minecraft:overworld", overworld);

        CompoundTag nether = new CompoundTag();
        nether.putString("type", "minecraft:the_nether");
        CompoundTag netherGen = new CompoundTag();
        netherGen.putString("type", "minecraft:flat");
        CompoundTag netherFlatSettings = new CompoundTag();
        netherFlatSettings.put("layers", new ListTag());
        netherFlatSettings.putString("biome", "minecraft:the_void");
        netherFlatSettings.putBoolean("features", false);
        netherFlatSettings.putBoolean("lakes", false);
        netherFlatSettings.put("structure_overrides", new ListTag());
        netherGen.put("settings", netherFlatSettings);
        nether.put("generator", netherGen);
        dimensions.put("minecraft:the_nether", nether);

        CompoundTag theEnd = new CompoundTag();
        theEnd.putString("type", "minecraft:the_end");
        CompoundTag endGen = new CompoundTag();
        endGen.putString("type", "minecraft:flat");
        CompoundTag endFlatSettings = new CompoundTag();
        endFlatSettings.put("layers", new ListTag());
        endFlatSettings.putString("biome", "minecraft:the_void");
        endFlatSettings.putBoolean("features", false);
        endFlatSettings.putBoolean("lakes", false);
        endFlatSettings.put("structure_overrides", new ListTag());
        endGen.put("settings", endFlatSettings);
        theEnd.put("generator", endGen);
        dimensions.put("minecraft:the_end", theEnd);

        worldGenSettings.put("dimensions", dimensions);
        data.put("WorldGenSettings", worldGenSettings);

        CompoundTag versionTag = new CompoundTag();
        versionTag.putInt("Id", 4189);
        versionTag.putString("Name", "1.21.1");
        versionTag.putString("Series", "main");
        versionTag.putBoolean("Snapshot", false);
        data.put("Version", versionTag);

        CompoundTag gameRules = new CompoundTag();
        gameRules.putString("announceAdvancements", "true");
        gameRules.putString("commandBlockOutput", "true");
        gameRules.putString("disableElytraMovementCheck", "false");
        gameRules.putString("disableRaids", "false");
        gameRules.putString("doDaylightCycle", "true");
        gameRules.putString("doEntityDrops", "true");
        gameRules.putString("doFireTick", "true");
        gameRules.putString("doInsomnia", "true");
        gameRules.putString("doImmediateRespawn", "false");
        gameRules.putString("doLimitedCrafting", "false");
        gameRules.putString("doMobLoot", "true");
        gameRules.putString("doMobSpawning", "false");
        gameRules.putString("doPatrolSpawning", "true");
        gameRules.putString("doTileDrops", "true");
        gameRules.putString("doTraderSpawning", "true");
        gameRules.putString("doWardenSpawning", "true");
        gameRules.putString("doWeatherCycle", "true");
        gameRules.putString("drowningDamage", "true");
        gameRules.putString("fallDamage", "true");
        gameRules.putString("fireDamage", "true");
        gameRules.putString("forgiveDeadPlayers", "true");
        gameRules.putString("freezeDamage", "true");
        gameRules.putString("keepInventory", "false");
        gameRules.putString("logAdminCommands", "true");
        gameRules.putString("maxCommandChainLength", "65536");
        gameRules.putString("maxEntityCramming", "24");
        gameRules.putString("mobGriefing", "true");
        gameRules.putString("naturalRegeneration", "true");
        gameRules.putString("randomTickSpeed", "0");
        gameRules.putString("reducedDebugInfo", "false");
        gameRules.putString("sendCommandFeedback", "true");
        gameRules.putString("showDeathMessages", "true");
        gameRules.putString("spawnRadius", "10");
        gameRules.putString("spectatorsGenerateChunks", "true");
        gameRules.putString("universalAnger", "false");
        data.put("GameRules", gameRules);

        root.put("Data", data);

        Path levelDatPath = saveDirectory.resolve("level.dat");
        NbtIo.writeCompressed(root, levelDatPath);

        LOGGER.info("Saved level.dat to {}", levelDatPath);
    }

    private void closeRegionFiles() {
        for (RegionFileWriter regionFile : regionFiles.values()) {
            try {
                if (regionFile != null) {
                    regionFile.close();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to close region file", e);
            }
        }
        regionFiles.clear();

        for (RegionFileWriter entityFile : entityRegionFiles.values()) {
            try {
                if (entityFile != null) {
                    entityFile.close();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to close entity region file", e);
            }
        }
        entityRegionFiles.clear();
    }

    private void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[ST] " + message), false);
        }
        LOGGER.info(message);
    }

    public int getChunksDownloaded() {
        return chunksDownloaded;
    }

    public void onDisconnect() {
        if (downloading) {
            stopDownload();
        }
    }

    private static class RegionFileWriter implements AutoCloseable {
        private static final int SECTOR_SIZE = 4096;
        private final RandomAccessFile file;
        private final int[] offsets = new int[1024];
        private final int[] timestamps = new int[1024];
        private int sectorsUsed = 2;

        public RegionFileWriter(Path path) throws IOException {
            Files.createDirectories(path.getParent());
            this.file = new RandomAccessFile(path.toFile(), "rw");

            if (file.length() < SECTOR_SIZE * 2) {
                file.seek(0);
                for (int i = 0; i < 1024; i++) {
                    file.writeInt(0);
                }
                for (int i = 0; i < 1024; i++) {
                    file.writeInt(0);
                }
            } else {
                file.seek(0);
                for (int i = 0; i < 1024; i++) {
                    offsets[i] = file.readInt();
                }
                for (int i = 0; i < 1024; i++) {
                    timestamps[i] = file.readInt();
                }
                for (int i = 0; i < 1024; i++) {
                    if (offsets[i] != 0) {
                        int offset = offsets[i] >> 8;
                        int size = offsets[i] & 0xFF;
                        sectorsUsed = Math.max(sectorsUsed, offset + size);
                    }
                }
            }
        }

        public synchronized void writeChunk(ChunkPos pos, CompoundTag tag) throws IOException {
            int localX = pos.x & 31;
            int localZ = pos.z & 31;
            int index = localX + localZ * 32;

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.DeflaterOutputStream dos = new java.util.zip.DeflaterOutputStream(baos);
            NbtIo.write(tag, new java.io.DataOutputStream(dos));
            dos.close();

            byte[] compressed = baos.toByteArray();
            int length = compressed.length + 5;
            int sectorsNeeded = (length + SECTOR_SIZE - 1) / SECTOR_SIZE;

            int sectorOffset = sectorsUsed;
            sectorsUsed += sectorsNeeded;

            file.seek((long) sectorOffset * SECTOR_SIZE);
            file.writeInt(compressed.length + 1);
            file.writeByte(2);
            file.write(compressed);

            int padding = (sectorsNeeded * SECTOR_SIZE) - length;
            for (int i = 0; i < padding; i++) {
                file.writeByte(0);
            }

            offsets[index] = (sectorOffset << 8) | sectorsNeeded;
            timestamps[index] = (int) (System.currentTimeMillis() / 1000);

            file.seek(index * 4L);
            file.writeInt(offsets[index]);

            file.seek(SECTOR_SIZE + index * 4L);
            file.writeInt(timestamps[index]);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }
}
