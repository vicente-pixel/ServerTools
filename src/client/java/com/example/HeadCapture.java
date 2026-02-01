package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Captures armor stands with player heads while walking around the world.
 * Saves them in TSV format: _id, zone, x, y, z
 */
public class HeadCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("HeadCapture");
    private static HeadCapture instance;

    private boolean capturing = false;
    private String currentZone = "HUB";
    private int nextId = 1;
    private final Set<BlockPos> capturedPositions = new HashSet<>();
    private Path outputFile;
    private int scanRadius = 50;

    private HeadCapture() {}

    public static HeadCapture getInstance() {
        if (instance == null) {
            instance = new HeadCapture();
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
            sendChatMessage("§c[Head Capture] Must be in a world!");
            return;
        }

        // Create output file in game directory
        String serverName = getServerName();
        String fileName = "head_captures_" + serverName + "_" + System.currentTimeMillis() + ".tsv";
        outputFile = mc.gameDirectory.toPath().resolve(fileName);

        try {
            // Write header
            String header = "_id\tzone\tx\ty\tz\n";
            Files.writeString(outputFile, header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to create output file", e);
            sendChatMessage("§c[Head Capture] Failed to create output file!");
            return;
        }

        capturedPositions.clear();
        nextId = 1;
        capturing = true;

        sendChatMessage("§a[Head Capture] Started capturing armor stands with player heads!");
        sendChatMessage("§e[Head Capture] Zone: " + currentZone + " | Radius: " + scanRadius);
        sendChatMessage("§7[Head Capture] Output: " + outputFile.getFileName());
    }

    public void stopCapture() {
        capturing = false;
        sendChatMessage("§c[Head Capture] Stopped capturing.");
        sendChatMessage("§a[Head Capture] Captured " + (nextId - 1) + " armor stands with player heads.");
        if (outputFile != null) {
            sendChatMessage("§7[Head Capture] Saved to: " + outputFile.getFileName());
        }
    }

    public void setZone(String zone) {
        this.currentZone = zone;
        sendChatMessage("§e[Head Capture] Zone set to: " + zone);
    }

    public void setScanRadius(int radius) {
        this.scanRadius = radius;
        sendChatMessage("§e[Head Capture] Scan radius set to: " + radius);
    }

    /**
     * Called every tick to scan for armor stands with player heads.
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

            // Check if it has a player head
            ItemStack headItem = armorStand.getItemBySlot(EquipmentSlot.HEAD);
            if (headItem.isEmpty()) continue;

            // Check if the item is a player head
            if (headItem.getItem() != Items.PLAYER_HEAD) continue;

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

            // Write to file
            String line = String.format("%d\t%s\t%d\t%d\t%d\n",
                nextId, currentZone, pos.getX(), pos.getY(), pos.getZ());

            try {
                Files.writeString(outputFile, line, StandardOpenOption.APPEND);
                LOGGER.info("Captured head #{} at {}, {}, {}", nextId, pos.getX(), pos.getY(), pos.getZ());

                // Send chat notification every 10 captures
                if (nextId % 10 == 0) {
                    sendChatMessage("§a[Head Capture] Captured " + nextId + " armor stands so far...");
                }

                nextId++;
            } catch (IOException e) {
                LOGGER.error("Failed to write to output file", e);
            }
        }
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
