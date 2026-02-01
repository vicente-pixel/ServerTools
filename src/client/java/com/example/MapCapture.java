package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Captures map art from item frames and stitches them into a single image.
 * Press H while looking at map art to capture.
 */
public class MapCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("MapCapture");
    private static MapCapture instance;

    // Map is 128x128 pixels
    private static final int MAP_SIZE = 128;

    // Minecraft map color palette (index -> ARGB color)
    private static final int[] MAP_COLORS = buildMapColorPalette();

    private MapCapture() {}

    public static MapCapture getInstance() {
        if (instance == null) {
            instance = new MapCapture();
        }
        return instance;
    }

    public void captureMapArt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        sendMessage("§e[Map Capture] Scanning for maps...");

        // Find all item frames with maps nearby
        List<MapFrame> mapFrames = findMapFrames(mc);

        if (mapFrames.isEmpty()) {
            sendMessage("§c[Map Capture] No maps found nearby!");
            return;
        }

        sendMessage("§a[Map Capture] Found " + mapFrames.size() + " maps, stitching...");

        // Group maps by their facing direction and determine grid
        Map<Direction, List<MapFrame>> byDirection = groupByDirection(mapFrames);

        // Process the largest group (most likely the map art wall)
        Direction mainDirection = null;
        int maxCount = 0;
        for (Map.Entry<Direction, List<MapFrame>> entry : byDirection.entrySet()) {
            if (entry.getValue().size() > maxCount) {
                maxCount = entry.getValue().size();
                mainDirection = entry.getKey();
            }
        }

        if (mainDirection == null) {
            sendMessage("§c[Map Capture] Could not determine map wall direction!");
            return;
        }

        List<MapFrame> wallMaps = byDirection.get(mainDirection);
        BufferedImage result = stitchMaps(wallMaps, mainDirection);

        if (result == null) {
            sendMessage("§c[Map Capture] Failed to stitch maps!");
            return;
        }

        // Save the image
        saveImage(result);
    }

    private List<MapFrame> findMapFrames(Minecraft mc) {
        List<MapFrame> result = new ArrayList<>();

        // Search in a radius around the player
        double radius = 32.0;
        AABB searchBox = mc.player.getBoundingBox().inflate(radius);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemFrame itemFrame)) {
                continue;
            }

            if (!searchBox.contains(entity.position())) {
                continue;
            }

            ItemStack item = itemFrame.getItem();
            if (item.isEmpty() || !(item.getItem() instanceof MapItem)) {
                continue;
            }

            MapId mapId = item.get(net.minecraft.core.component.DataComponents.MAP_ID);
            if (mapId == null) {
                continue;
            }

            MapItemSavedData mapData = mc.level.getMapData(mapId);
            if (mapData == null) {
                continue;
            }

            result.add(new MapFrame(
                itemFrame,
                itemFrame.getDirection(),
                itemFrame.blockPosition(),
                mapId,
                mapData
            ));
        }

        return result;
    }

    private Map<Direction, List<MapFrame>> groupByDirection(List<MapFrame> frames) {
        Map<Direction, List<MapFrame>> result = new HashMap<>();
        for (MapFrame frame : frames) {
            result.computeIfAbsent(frame.direction, k -> new ArrayList<>()).add(frame);
        }
        return result;
    }

    private BufferedImage stitchMaps(List<MapFrame> maps, Direction facing) {
        if (maps.isEmpty()) {
            return null;
        }

        // Determine the grid bounds
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        // For maps on a wall, we need to use the appropriate axes based on facing direction
        for (MapFrame map : maps) {
            int[] coords = getGridCoords(map.pos, facing);
            minX = Math.min(minX, coords[0]);
            maxX = Math.max(maxX, coords[0]);
            minY = Math.min(minY, coords[1]);
            maxY = Math.max(maxY, coords[1]);
        }

        int gridWidth = maxX - minX + 1;
        int gridHeight = maxY - minY + 1;

        LOGGER.info("Map grid: {}x{} (from {},{} to {},{})", gridWidth, gridHeight, minX, minY, maxX, maxY);

        // Create the output image
        BufferedImage result = new BufferedImage(
            gridWidth * MAP_SIZE,
            gridHeight * MAP_SIZE,
            BufferedImage.TYPE_INT_ARGB
        );

        // Place each map in the grid (left to right, top to bottom from viewer's perspective)
        for (MapFrame map : maps) {
            int[] coords = getGridCoords(map.pos, facing);
            int gridX = coords[0] - minX;
            int gridY = coords[1] - minY;

            // Render map data to image
            renderMapToImage(result, map.mapData, gridX * MAP_SIZE, gridY * MAP_SIZE, facing);
        }

        return result;
    }

    private int[] getGridCoords(BlockPos pos, Direction facing) {
        // Return [x, y] grid coordinates based on facing direction
        // The viewer looks AT the wall, so we need coords from their perspective
        // X increases left-to-right from viewer's perspective
        // Y increases top-to-bottom (image coords)

        return switch (facing) {
            // Wall faces NORTH means viewer looks SOUTH at it
            // Viewer's left is +X, viewer's up is +Y
            case NORTH -> new int[]{-pos.getX(), -pos.getY()};
            // Wall faces SOUTH means viewer looks NORTH at it
            // Viewer's left is -X, viewer's up is +Y
            case SOUTH -> new int[]{pos.getX(), -pos.getY()};
            // Wall faces EAST means viewer looks WEST at it
            // Viewer's left is +Z, viewer's up is +Y
            case EAST -> new int[]{-pos.getZ(), -pos.getY()};
            // Wall faces WEST means viewer looks EAST at it
            // Viewer's left is -Z, viewer's up is +Y
            case WEST -> new int[]{pos.getZ(), -pos.getY()};
            // Floor/ceiling
            case UP -> new int[]{pos.getX(), -pos.getZ()};
            case DOWN -> new int[]{pos.getX(), pos.getZ()};
        };
    }

    private void renderMapToImage(BufferedImage image, MapItemSavedData mapData, int offsetX, int offsetY, Direction facing) {
        byte[] colors = mapData.colors;

        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int colorIndex = colors[x + y * MAP_SIZE] & 0xFF;
                int argb = getMapColor(colorIndex);

                // Map data is stored as viewed from above (north up)
                // We need to rotate based on which wall the map is on
                int px, py;

                switch (facing) {
                    case SOUTH -> {
                        // Rotated 180 degrees
                        px = MAP_SIZE - 1 - x;
                        py = MAP_SIZE - 1 - y;
                    }
                    case EAST -> {
                        // Rotated 90 degrees clockwise
                        px = MAP_SIZE - 1 - y;
                        py = x;
                    }
                    case WEST -> {
                        // Rotated 90 degrees counter-clockwise
                        px = y;
                        py = MAP_SIZE - 1 - x;
                    }
                    default -> {
                        // NORTH, UP, DOWN - no rotation needed
                        px = x;
                        py = y;
                    }
                }

                image.setRGB(offsetX + px, offsetY + py, argb);
            }
        }
    }

    private int getMapColor(int colorIndex) {
        if (colorIndex >= 0 && colorIndex < MAP_COLORS.length) {
            return MAP_COLORS[colorIndex];
        }
        return 0x00000000; // Transparent for invalid indices
    }

    private static int[] buildMapColorPalette() {
        // Minecraft map color palette
        // Base colors with 4 shades each (multipliers: 180, 220, 255, 135 out of 255)
        int[][] baseColors = {
            {0, 0, 0, 0},           // 0: NONE (transparent)
            {127, 178, 56},         // 1: GRASS
            {247, 233, 163},        // 2: SAND
            {199, 199, 199},        // 3: WOOL
            {255, 0, 0},            // 4: FIRE
            {160, 160, 255},        // 5: ICE
            {167, 167, 167},        // 6: METAL
            {0, 124, 0},            // 7: PLANT
            {255, 255, 255},        // 8: SNOW
            {164, 168, 184},        // 9: CLAY
            {151, 109, 77},         // 10: DIRT
            {112, 112, 112},        // 11: STONE
            {64, 64, 255},          // 12: WATER
            {143, 119, 72},         // 13: WOOD
            {255, 252, 245},        // 14: QUARTZ
            {216, 127, 51},         // 15: COLOR_ORANGE
            {178, 76, 216},         // 16: COLOR_MAGENTA
            {102, 153, 216},        // 17: COLOR_LIGHT_BLUE
            {229, 229, 51},         // 18: COLOR_YELLOW
            {127, 204, 25},         // 19: COLOR_LIGHT_GREEN
            {242, 127, 165},        // 20: COLOR_PINK
            {76, 76, 76},           // 21: COLOR_GRAY
            {153, 153, 153},        // 22: COLOR_LIGHT_GRAY
            {76, 127, 153},         // 23: COLOR_CYAN
            {127, 63, 178},         // 24: COLOR_PURPLE
            {51, 76, 178},          // 25: COLOR_BLUE
            {102, 76, 51},          // 26: COLOR_BROWN
            {102, 127, 51},         // 27: COLOR_GREEN
            {153, 51, 51},          // 28: COLOR_RED
            {25, 25, 25},           // 29: COLOR_BLACK
            {250, 238, 77},         // 30: GOLD
            {92, 219, 213},         // 31: DIAMOND
            {74, 128, 255},         // 32: LAPIS
            {0, 217, 58},           // 33: EMERALD
            {129, 86, 49},          // 34: PODZOL
            {112, 2, 0},            // 35: NETHER
            {209, 177, 161},        // 36: TERRACOTTA_WHITE
            {159, 82, 36},          // 37: TERRACOTTA_ORANGE
            {149, 87, 108},         // 38: TERRACOTTA_MAGENTA
            {112, 108, 138},        // 39: TERRACOTTA_LIGHT_BLUE
            {186, 133, 36},         // 40: TERRACOTTA_YELLOW
            {103, 117, 53},         // 41: TERRACOTTA_LIGHT_GREEN
            {160, 77, 78},          // 42: TERRACOTTA_PINK
            {57, 41, 35},           // 43: TERRACOTTA_GRAY
            {135, 107, 98},         // 44: TERRACOTTA_LIGHT_GRAY
            {87, 92, 92},           // 45: TERRACOTTA_CYAN
            {122, 73, 88},          // 46: TERRACOTTA_PURPLE
            {76, 62, 92},           // 47: TERRACOTTA_BLUE
            {76, 50, 35},           // 48: TERRACOTTA_BROWN
            {76, 82, 42},           // 49: TERRACOTTA_GREEN
            {142, 60, 46},          // 50: TERRACOTTA_RED
            {37, 22, 16},           // 51: TERRACOTTA_BLACK
            {189, 48, 49},          // 52: CRIMSON_NYLIUM
            {148, 63, 97},          // 53: CRIMSON_STEM
            {92, 25, 29},           // 54: CRIMSON_HYPHAE
            {22, 126, 134},         // 55: WARPED_NYLIUM
            {58, 142, 140},         // 56: WARPED_STEM
            {86, 44, 62},           // 57: WARPED_HYPHAE
            {20, 180, 133},         // 58: WARPED_WART_BLOCK
            {100, 100, 100},        // 59: DEEPSLATE
            {216, 175, 147},        // 60: RAW_IRON
            {127, 167, 150},        // 61: GLOW_LICHEN
        };

        int[] palette = new int[256];

        // Color 0 is always transparent
        palette[0] = 0x00000000;

        // Each base color has 4 shades
        double[] shadeMultipliers = {180.0/255.0, 220.0/255.0, 1.0, 135.0/255.0};

        for (int i = 1; i < baseColors.length; i++) {
            int[] base = baseColors[i];
            for (int shade = 0; shade < 4; shade++) {
                int index = i * 4 + shade;
                if (index >= 256) break;

                double mult = shadeMultipliers[shade];
                int r = Math.min(255, (int)(base[0] * mult));
                int g = Math.min(255, (int)(base[1] * mult));
                int b = Math.min(255, (int)(base[2] * mult));

                palette[index] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        return palette;
    }

    private void saveImage(BufferedImage image) {
        try {
            File captureDir = new File("map_captures");
            if (!captureDir.exists()) {
                captureDir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File outputFile = new File(captureDir, "mapart_" + timestamp + ".png");

            ImageIO.write(image, "PNG", outputFile);

            sendMessage("§a[Map Capture] Saved: map_captures/" + outputFile.getName());
            sendMessage("§7Size: " + image.getWidth() + "x" + image.getHeight() + " pixels");

            LOGGER.info("Saved map art to: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            sendMessage("§c[Map Capture] Failed to save: " + e.getMessage());
            LOGGER.error("Failed to save map capture", e);
        }
    }

    private void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.execute(() -> {
                mc.player.displayClientMessage(Component.literal(message), false);
            });
        }
    }

    private record MapFrame(
        ItemFrame frame,
        Direction direction,
        BlockPos pos,
        MapId mapId,
        MapItemSavedData mapData
    ) {}
}
