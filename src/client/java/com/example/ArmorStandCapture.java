package com.example;

import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Captures armor stands with a specific player head texture while walking.
 * Toggle with P key - saves to CSV file with format: _id, zone, x, y, z
 */
public class ArmorStandCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("ArmorStandCapture");
    private static ArmorStandCapture instance;

    // Only capture armor stands with this specific texture
    private static final String TARGET_TEXTURE = "http://textures.minecraft.net/texture/299ea120bd83d0c81a3c4627f5bce1b12fb03bcb57779c63dcc77e3f4ae8a793";

    private boolean capturing = false;
    private String currentZone = "HUB";
    private int nextId = 1;
    private final Set<BlockPos> capturedPositions = new HashSet<>();
    private Path outputFile;
    private int scanRadius = 50;

    private ArmorStandCapture() {}

    public static ArmorStandCapture getInstance() {
        if (instance == null) {
            instance = new ArmorStandCapture();
        }
        return instance;
    }

    public void toggleCapture() {
        if (capturing) {
            stopCapture();
        } else {
            startCapture();
        }
    }

    public void startCapture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            sendChatMessage("§c[Armor Stand Capture] Must be in a world!");
            return;
        }

        // Create output file in game directory
        String serverName = getServerName();
        String fileName = "armorstand_captures_" + serverName + "_" + System.currentTimeMillis() + ".csv";
        outputFile = mc.gameDirectory.toPath().resolve(fileName);

        try {
            // Write header (tab-separated like the example)
            String header = "_id\tzone\tx\ty\tz\n";
            Files.writeString(outputFile, header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to create output file", e);
            sendChatMessage("§c[Armor Stand Capture] Failed to create output file!");
            return;
        }

        capturedPositions.clear();
        nextId = 1;
        capturing = true;

        sendChatMessage("§a[Armor Stand Capture] Started capturing!");
        sendChatMessage("§e[Armor Stand Capture] Zone: " + currentZone + " | Radius: " + scanRadius);
        sendChatMessage("§7[Armor Stand Capture] Looking for texture: " + TARGET_TEXTURE.substring(TARGET_TEXTURE.lastIndexOf("/") + 1, Math.min(TARGET_TEXTURE.lastIndexOf("/") + 13, TARGET_TEXTURE.length())) + "...");
        sendChatMessage("§7[Armor Stand Capture] Output: " + outputFile.getFileName());
    }

    public void stopCapture() {
        capturing = false;
        sendChatMessage("§c[Armor Stand Capture] Stopped capturing.");
        sendChatMessage("§a[Armor Stand Capture] Captured " + (nextId - 1) + " armor stands.");
        if (outputFile != null) {
            sendChatMessage("§7[Armor Stand Capture] Saved to: " + outputFile.getFileName());
        }
    }

    public void setZone(String zone) {
        this.currentZone = zone;
        sendChatMessage("§e[Armor Stand Capture] Zone set to: " + zone);
    }

    public void setScanRadius(int radius) {
        this.scanRadius = radius;
        sendChatMessage("§e[Armor Stand Capture] Scan radius set to: " + radius);
    }

    /**
     * Called every tick to scan for armor stands with the target texture.
     */
    public void onTick() {
        if (!capturing) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) return;

        // Define search area around player
        AABB searchBox = new AABB(
            player.getX() - scanRadius,
            level.getMinY(),
            player.getZ() - scanRadius,
            player.getX() + scanRadius,
            level.getMaxY(),
            player.getZ() + scanRadius
        );

        // Find all armor stands in the area
        for (Entity entity : level.getEntities(player, searchBox, e -> e instanceof ArmorStand)) {
            ArmorStand armorStand = (ArmorStand) entity;

            // Check if it has the target texture
            String textureUrl = getHeadTextureUrl(armorStand);
            if (textureUrl == null || !textureUrl.equals(TARGET_TEXTURE)) {
                continue;
            }

            // Get block position (rounded coords) - Y offset +2 for fairy souls
            BlockPos pos = new BlockPos(
                (int) Math.floor(armorStand.getX()),
                (int) Math.floor(armorStand.getY()) + 2,
                (int) Math.floor(armorStand.getZ())
            );

            // Skip if already captured
            if (capturedPositions.contains(pos)) continue;

            // Add to captured set
            capturedPositions.add(pos);

            // Write to file (tab-separated)
            String line = String.format("%d\t%s\t%d\t%d\t%d\n",
                nextId, currentZone, pos.getX(), pos.getY(), pos.getZ());

            try {
                Files.writeString(outputFile, line, StandardOpenOption.APPEND);
                LOGGER.info("Captured armor stand #{} at {}, {}, {}", nextId, pos.getX(), pos.getY(), pos.getZ());

                // Send chat notification every 10 captures
                if (nextId % 10 == 0) {
                    sendChatMessage("§a[Armor Stand Capture] Captured " + nextId + " armor stands so far...");
                }

                nextId++;
            } catch (IOException e) {
                LOGGER.error("Failed to write to output file", e);
            }
        }
    }

    /**
     * Gets the texture URL from an armor stand's head slot, if it has a player head.
     */
    private String getHeadTextureUrl(ArmorStand armorStand) {
        ItemStack headItem = armorStand.getItemBySlot(EquipmentSlot.HEAD);
        if (headItem.isEmpty() || !headItem.has(DataComponents.PROFILE)) {
            return null;
        }

        ResolvableProfile profile = headItem.get(DataComponents.PROFILE);
        if (profile == null) {
            return null;
        }

        try {
            var gameProfile = profile.partialProfile();
            var properties = gameProfile.properties();
            if (properties != null && properties.containsKey("textures")) {
                for (Property property : properties.get("textures")) {
                    String textureValue = property.value();
                    try {
                        String decoded = new String(Base64.getDecoder().decode(textureValue));
                        return extractSkinUrl(decoded);
                    } catch (Exception e) {
                        // Couldn't decode
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to get head texture URL", e);
        }

        return null;
    }

    private String extractSkinUrl(String decoded) {
        // Extract URL from JSON like: {"textures":{"SKIN":{"url":"http://..."}}}
        try {
            int skinIndex = decoded.indexOf("\"SKIN\"");
            if (skinIndex == -1) {
                skinIndex = decoded.indexOf("SKIN");
            }
            if (skinIndex == -1) return null;

            int urlIndex = decoded.indexOf("\"url\"", skinIndex);
            if (urlIndex == -1) {
                urlIndex = decoded.indexOf("url", skinIndex);
            }
            if (urlIndex == -1) return null;

            int colonIndex = decoded.indexOf(":", urlIndex);
            if (colonIndex == -1) return null;

            int urlStart = decoded.indexOf("\"", colonIndex) + 1;
            int urlEnd = decoded.indexOf("\"", urlStart);

            if (urlStart > 0 && urlEnd > urlStart) {
                return decoded.substring(urlStart, urlEnd);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract skin URL", e);
        }
        return null;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public int getCapturedCount() {
        return nextId - 1;
    }

    public String getCurrentZone() {
        return currentZone;
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

    private void sendChatMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.execute(() -> {
                client.player.displayClientMessage(Component.literal(message), false);
            });
        }
    }
}
