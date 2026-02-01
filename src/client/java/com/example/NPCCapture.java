package com.example;

import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Captures NPC data including skin texture, hologram text, location, and held items.
 */
public class NPCCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("NPCCapture");
    private static NPCCapture instance;

    private NPCCapture() {}

    public static NPCCapture getInstance() {
        if (instance == null) {
            instance = new NPCCapture();
        }
        return instance;
    }

    public void captureTargetedNPC() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) {
            sendChatMessage("§c[NPC Capture] Not in a world");
            return;
        }

        // Raycast to find entity player is looking at
        Entity targetEntity = getTargetedEntity(mc, player, 20.0);

        if (targetEntity == null) {
            sendChatMessage("§c[NPC Capture] No entity in view (look at an NPC)");
            return;
        }

        String output = captureNPCData(mc, level, targetEntity);
        copyToClipboard(output);
        sendChatMessage("§a[NPC Capture] Copied NPC data to clipboard!");
    }

    private Entity getTargetedEntity(Minecraft mc, LocalPlayer player, double reach) {
        // First check if crosshair target is an entity
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) mc.hitResult).getEntity();
        }

        // Manual raycast for entities
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(reach)).inflate(1.0);

        Entity closest = null;
        double closestDist = reach;

        for (Entity entity : mc.level.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable())) {
            AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            var intersection = entityBox.clip(eyePos, endPos);

            if (intersection.isPresent()) {
                double dist = eyePos.distanceTo(intersection.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }

        return closest;
    }

    private String captureNPCData(Minecraft mc, ClientLevel level, Entity entity) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== NPC CAPTURE ===\n\n");

        // Entity type and basic info
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        sb.append("Entity Type: ").append(entityType).append("\n");
        sb.append("Entity ID: ").append(entity.getId()).append("\n");

        // Custom name if present
        if (entity.hasCustomName()) {
            Component customName = entity.getCustomName();
            sb.append("Name: ").append(componentToColorString(customName)).append("\n");
        }

        // Location
        sb.append("\n--- Location ---\n");
        sb.append("Position: ").append(String.format("%.2f, %.2f, %.2f", entity.getX(), entity.getY(), entity.getZ())).append("\n");
        sb.append("Rotation: Yaw=").append(String.format("%.1f", entity.getYRot()))
          .append(", Pitch=").append(String.format("%.1f", entity.getXRot())).append("\n");

        // Skin texture (for player entities / NPCs)
        if (entity instanceof Player playerEntity) {
            sb.append("\n--- Skin Data ---\n");
            captureSkinData(mc, playerEntity, sb);
        }

        // Held items
        if (entity instanceof LivingEntity livingEntity) {
            sb.append("\n--- Equipment ---\n");
            captureEquipment(livingEntity, sb);
        }

        // Find holograms (armor stands above the entity)
        sb.append("\n--- Holograms ---\n");
        captureHolograms(level, entity, sb);

        return sb.toString();
    }

    private void captureSkinData(Minecraft mc, Player playerEntity, StringBuilder sb) {
        try {
            // Get PlayerInfo from the connection
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(playerEntity.getUUID());

            if (playerInfo != null) {
                sb.append("Player Name: ").append(playerInfo.getProfile().name()).append("\n");
                sb.append("UUID: ").append(playerInfo.getProfile().id()).append("\n");

                // Get skin texture from profile
                var properties = playerInfo.getProfile().properties();
                if (properties != null && properties.containsKey("textures")) {
                    for (Property property : properties.get("textures")) {
                        String textureValue = property.value();
                        sb.append("Texture (Base64): ").append(textureValue).append("\n");

                        // Decode to get the actual URL
                        try {
                            String decoded = new String(Base64.getDecoder().decode(textureValue));
                            sb.append("Texture Data: ").append(decoded).append("\n");

                            // Extract just the URL for convenience
                            if (decoded.contains("\"url\"")) {
                                int urlStart = decoded.indexOf("\"url\"");
                                int urlValueStart = decoded.indexOf("\"", urlStart + 6) + 1;
                                int urlValueEnd = decoded.indexOf("\"", urlValueStart);
                                if (urlValueEnd > urlValueStart) {
                                    String skinUrl = decoded.substring(urlValueStart, urlValueEnd);
                                    sb.append("Skin URL: ").append(skinUrl).append("\n");
                                }
                            }
                        } catch (Exception e) {
                            // Couldn't decode
                        }

                        if (property.signature() != null) {
                            sb.append("Signature: ").append(property.signature()).append("\n");
                        }
                    }
                } else {
                    sb.append("(No custom skin texture)\n");
                }
            } else {
                sb.append("(Could not get player info)\n");
            }
        } catch (Exception e) {
            sb.append("(Error getting skin: ").append(e.getMessage()).append(")\n");
        }
    }

    private void captureEquipment(LivingEntity entity, StringBuilder sb) {
        boolean hasEquipment = false;

        // Main hand
        ItemStack mainHand = entity.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!mainHand.isEmpty()) {
            sb.append("Main Hand: ").append(formatItem(mainHand)).append("\n");
            hasEquipment = true;
        }

        // Off hand
        ItemStack offHand = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offHand.isEmpty()) {
            sb.append("Off Hand: ").append(formatItem(offHand)).append("\n");
            hasEquipment = true;
        }

        // Head
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty()) {
            sb.append("Head: ").append(formatItem(head)).append("\n");
            hasEquipment = true;
        }

        // Chest
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) {
            sb.append("Chest: ").append(formatItem(chest)).append("\n");
            hasEquipment = true;
        }

        // Legs
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        if (!legs.isEmpty()) {
            sb.append("Legs: ").append(formatItem(legs)).append("\n");
            hasEquipment = true;
        }

        // Feet
        ItemStack feet = entity.getItemBySlot(EquipmentSlot.FEET);
        if (!feet.isEmpty()) {
            sb.append("Feet: ").append(formatItem(feet)).append("\n");
            hasEquipment = true;
        }

        if (!hasEquipment) {
            sb.append("(No equipment)\n");
        }
    }

    private String formatItem(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        sb.append(itemId);

        if (stack.getCount() > 1) {
            sb.append(" x").append(stack.getCount());
        }

        // Custom name with color codes
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                sb.append(" [").append(componentToColorString(customName)).append("]");
            }
        }

        return sb.toString();
    }

    private void captureHolograms(ClientLevel level, Entity npcEntity, StringBuilder sb) {
        // Search for armor stands above the NPC (holograms are usually invisible armor stands)
        double searchRadius = 3.0;
        double maxHeightAbove = 5.0;

        AABB searchBox = new AABB(
            npcEntity.getX() - searchRadius,
            npcEntity.getY(),
            npcEntity.getZ() - searchRadius,
            npcEntity.getX() + searchRadius,
            npcEntity.getY() + maxHeightAbove,
            npcEntity.getZ() + searchRadius
        );

        List<ArmorStand> holograms = new ArrayList<>();

        for (Entity entity : level.getEntities(npcEntity, searchBox, e -> e instanceof ArmorStand)) {
            ArmorStand armorStand = (ArmorStand) entity;

            // Holograms are usually invisible, have custom names, and are markers
            if (armorStand.hasCustomName()) {
                holograms.add(armorStand);
            }
        }

        if (holograms.isEmpty()) {
            sb.append("(No holograms found)\n");
            return;
        }

        // Sort by Y position (top to bottom)
        holograms.sort(Comparator.comparingDouble((ArmorStand a) -> a.getY()).reversed());

        sb.append("Found ").append(holograms.size()).append(" hologram line(s):\n");

        for (int i = 0; i < holograms.size(); i++) {
            ArmorStand hologram = holograms.get(i);
            Component name = hologram.getCustomName();

            sb.append("  Line ").append(i + 1).append(": ");
            sb.append(componentToColorString(name));
            sb.append("\n");

            // Also show position for precise recreation
            sb.append("    Position: ").append(String.format("%.2f, %.2f, %.2f",
                hologram.getX(), hologram.getY(), hologram.getZ())).append("\n");
        }
    }

    /**
     * Converts a Component to a string preserving Minecraft color codes (§).
     */
    private String componentToColorString(Component component) {
        if (component == null) return "";

        StringBuilder result = new StringBuilder();
        appendComponentWithColors(component, result);
        return result.toString();
    }

    private void appendComponentWithColors(Component component, StringBuilder sb) {
        Style style = component.getStyle();

        // Build formatting prefix
        StringBuilder prefix = new StringBuilder();

        // Add color code first
        TextColor color = style.getColor();
        if (color != null) {
            String colorCode = getColorCode(color);
            if (colorCode != null) {
                prefix.append(colorCode);
            }
        }

        // Add formatting codes
        if (style.isBold()) prefix.append("§l");
        if (style.isItalic()) prefix.append("§o");
        if (style.isUnderlined()) prefix.append("§n");
        if (style.isStrikethrough()) prefix.append("§m");
        if (style.isObfuscated()) prefix.append("§k");

        boolean hasFormatting = prefix.length() > 0;

        if (hasFormatting) {
            sb.append(prefix);
        }

        // Get the literal content
        var contents = component.getContents();
        if (contents instanceof PlainTextContents.LiteralContents literal) {
            sb.append(literal.text());
        } else if (contents instanceof TranslatableContents translatable) {
            String fallback = translatable.getFallback();
            if (fallback != null) {
                sb.append(fallback);
            } else {
                sb.append(translatable.getKey());
            }
        }

        // Reset after this component if it had formatting
        if (hasFormatting) {
            sb.append("§r");
        }

        // Process siblings
        for (Component sibling : component.getSiblings()) {
            appendComponentWithColors(sibling, sb);
        }
    }

    private String getColorCode(TextColor color) {
        if (color == null) return null;

        int value = color.getValue();

        // Standard Minecraft colors
        if (value == 0x000000) return "§0"; // black
        if (value == 0x0000AA) return "§1"; // dark_blue
        if (value == 0x00AA00) return "§2"; // dark_green
        if (value == 0x00AAAA) return "§3"; // dark_aqua
        if (value == 0xAA0000) return "§4"; // dark_red
        if (value == 0xAA00AA) return "§5"; // dark_purple
        if (value == 0xFFAA00) return "§6"; // gold
        if (value == 0xAAAAAA) return "§7"; // gray
        if (value == 0x555555) return "§8"; // dark_gray
        if (value == 0x5555FF) return "§9"; // blue
        if (value == 0x55FF55) return "§a"; // green
        if (value == 0x55FFFF) return "§b"; // aqua
        if (value == 0xFF5555) return "§c"; // red
        if (value == 0xFF55FF) return "§d"; // light_purple
        if (value == 0xFFFF55) return "§e"; // yellow
        if (value == 0xFFFFFF) return "§f"; // white

        // Custom hex color
        return "§#" + String.format("%06X", value);
    }

    private void copyToClipboard(String content) {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(content);
        } catch (Exception e) {
            LOGGER.error("Could not copy to clipboard", e);
        }
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
