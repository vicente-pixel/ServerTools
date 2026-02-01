package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ResolvableProfile;
import com.mojang.authlib.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

/**
 * Captures open menus/screens and exports them with full details.
 * Preserves color codes, full names, and complete lore text.
 */
public class MenuCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("MenuCapture");
    private static MenuCapture instance;

    private MenuCapture() {}

    public static MenuCapture getInstance() {
        if (instance == null) {
            instance = new MenuCapture();
        }
        return instance;
    }

    public void captureCurrentMenu() {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        if (currentScreen == null) {
            sendChatMessage("§c[Menu Capture] No menu open");
            return;
        }

        // ONLY capture container screens (chests, etc.) - NOT player inventory
        if (!(currentScreen instanceof AbstractContainerScreen<?>)) {
            sendChatMessage("§e[Menu Capture] Not a container menu");
            return;
        }

        // Skip player inventory screens
        if (currentScreen instanceof InventoryScreen || currentScreen instanceof CreativeModeInventoryScreen) {
            sendChatMessage("§e[Menu Capture] Use on chests/menus, not inventory");
            return;
        }

        String output = captureScreen(currentScreen);
        copyToClipboard(output);
        sendChatMessage("§a[Menu Capture] Copied to clipboard!");
    }

    private String captureScreen(Screen screen) {
        StringBuilder sb = new StringBuilder();

        // Screen type - full class name
        String screenType = screen.getClass().getSimpleName();
        sb.append("Screen Type: ").append(screenType).append("\n");

        // Screen title with color codes preserved
        if (screen.getTitle() != null) {
            String title = componentToColorString(screen.getTitle());
            if (!title.isEmpty()) {
                sb.append("Title: ").append(title).append("\n");
            }
        }

        // Screen dimensions
        sb.append("Dimensions: ").append(screen.width).append("x").append(screen.height).append("\n");

        // If it's a container screen, capture inventory/slots
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            sb.append(captureContainer(containerScreen));
        }

        // Capture widgets (buttons, text fields, etc.)
        sb.append(captureWidgets(screen));

        return sb.toString();
    }

    /**
     * Converts a Component to a string preserving Minecraft color codes (§).
     * Properly handles nested components with their individual styles.
     */
    private String componentToColorString(Component component) {
        if (component == null) return "";

        StringBuilder result = new StringBuilder();
        appendComponentWithColors(component, result, Style.EMPTY);
        return result.toString();
    }

    private void appendComponentWithColors(Component component, StringBuilder sb, Style inheritedStyle) {
        // Get the effective style (component's style merged with inherited)
        Style style = component.getStyle();

        // Build formatting prefix
        StringBuilder prefix = new StringBuilder();

        // Add color code first (color should come before formatting)
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

        // Get the literal content of THIS component only (not children)
        // Use Contents to get just this component's text
        var contents = component.getContents();
        if (contents instanceof PlainTextContents.LiteralContents literal) {
            sb.append(literal.text());
        } else if (contents instanceof TranslatableContents translatable) {
            // For translatable content, try to get the fallback or key
            String fallback = translatable.getFallback();
            if (fallback != null) {
                sb.append(fallback);
            } else {
                sb.append(translatable.getKey());
            }
        }

        // Reset after this component's content if it had formatting
        if (hasFormatting) {
            sb.append("§r");
        }

        // Process siblings (child components)
        for (Component sibling : component.getSiblings()) {
            appendComponentWithColors(sibling, sb, style);
        }
    }

    private String getColorCode(TextColor color) {
        if (color == null) return null;

        // Try to match standard Minecraft colors
        int value = color.getValue();

        // Standard Minecraft color values
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

        // For custom colors, use hex format
        return "§#" + String.format("%06X", value);
    }

    private String captureContainer(AbstractContainerScreen<?> screen) {
        StringBuilder sb = new StringBuilder();
        AbstractContainerMenu menu = screen.getMenu();
        Minecraft mc = Minecraft.getInstance();

        sb.append("\n=== CONTAINER SLOTS ===\n");

        List<Slot> slots = menu.slots;
        int containerSlotCount = 0;
        int nonEmptyCount = 0;

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);

            // Skip player inventory slots - only capture container slots
            // Player inventory slots have their container set to player.getInventory()
            if (mc.player != null && slot.container == mc.player.getInventory()) {
                continue;
            }

            containerSlotCount++;
            ItemStack stack = slot.getItem();

            if (!stack.isEmpty()) {
                sb.append("Slot ").append(i).append(": ").append(formatItem(stack)).append("\n");
                nonEmptyCount++;
            }
        }

        if (nonEmptyCount == 0) {
            sb.append("(All slots empty)\n");
        }

        sb.append("Total Container Slots: ").append(containerSlotCount).append(" (").append(nonEmptyCount).append(" with items)\n");

        return sb.toString();
    }

    private String formatItem(ItemStack stack) {
        StringBuilder sb = new StringBuilder();

        // Item ID (full path)
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        sb.append(itemId);

        // Count
        if (stack.getCount() > 1) {
            sb.append(" x").append(stack.getCount());
        }

        // Custom name with colors preserved
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                sb.append("\n    Name: ").append(componentToColorString(customName));
            }
        }

        // Enchantments (full names)
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants != null && !enchants.isEmpty()) {
            sb.append("\n    Enchantments:");
            enchants.entrySet().forEach(entry -> {
                // Get enchantment name from the holder's string representation
                String fullName = entry.getKey().toString();
                String enchName = fullName;
                // Extract name from format like "Reference{...minecraft:sharpness}"
                if (fullName.contains(":")) {
                    int colonIdx = fullName.lastIndexOf(":");
                    int endIdx = fullName.indexOf("}", colonIdx);
                    if (endIdx < 0) endIdx = fullName.length();
                    enchName = fullName.substring(colonIdx + 1, endIdx);
                }
                // Use full enchantment name, convert underscores to spaces and capitalize
                enchName = formatEnchantName(enchName);
                int level = entry.getIntValue();
                sb.append("\n      - ").append(enchName);
                if (level > 1) {
                    sb.append(" ").append(toRoman(level));
                }
            });
        }

        // Durability if applicable
        if (stack.isDamaged()) {
            int maxDmg = stack.getMaxDamage();
            int dmg = stack.getDamageValue();
            int durability = maxDmg - dmg;
            sb.append("\n    Durability: ").append(durability).append("/").append(maxDmg);
        }

        // Lore with colors preserved
        var lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            sb.append("\n    Lore:");
            for (Component line : lore.lines()) {
                sb.append("\n      ").append(componentToColorString(line));
            }
        }

        // Skull texture (for player heads with custom textures)
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile != null) {
            sb.append("\n    Skull Profile:");

            try {
                // Get the name if available
                if (profile.name().isPresent()) {
                    sb.append("\n      Owner: ").append(profile.name().get());
                }

                // Get the GameProfile which contains UUID and properties
                var gameProfile = profile.partialProfile();

                // Get the UUID (GameProfile is a record, uses id() not getId())
                if (gameProfile.id() != null) {
                    sb.append("\n      UUID: ").append(gameProfile.id().toString());
                }

                // Get the texture data from properties
                var properties = gameProfile.properties();
                if (properties != null && properties.containsKey("textures")) {
                    for (Property property : properties.get("textures")) {
                        String textureValue = property.value();
                        sb.append("\n      Texture: ").append(textureValue);

                        // Decode the base64 texture to show the URL
                        try {
                            String decoded = new String(Base64.getDecoder().decode(textureValue));
                            sb.append("\n      Texture Data: ").append(decoded);
                        } catch (Exception e) {
                            // Couldn't decode, just show the raw value
                        }

                        // Include signature if present
                        if (property.signature() != null) {
                            sb.append("\n      Signature: ").append(property.signature());
                        }
                    }
                }
            } catch (Exception e) {
                sb.append("\n      (Could not read profile: ").append(e.getMessage()).append(")");
            }
        }

        return sb.toString();
    }

    /**
     * Formats enchantment name: replaces underscores with spaces and capitalizes words.
     */
    private String formatEnchantName(String name) {
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }
        return result.toString();
    }

    /**
     * Converts a number to Roman numerals (for enchantment levels).
     */
    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    private String captureWidgets(Screen screen) {
        StringBuilder sb = new StringBuilder();
        List<String> widgets = new ArrayList<>();

        try {
            // Use reflection to access renderables field
            Field renderablesField = Screen.class.getDeclaredField("renderables");
            renderablesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<?> renderables = (List<?>) renderablesField.get(screen);

            for (Object obj : renderables) {
                if (obj instanceof Button btn) {
                    String msg = componentToColorString(btn.getMessage());
                    if (!msg.isEmpty()) {
                        String state = btn.active ? "Active" : "Inactive";
                        widgets.add("Button: " + msg + " [" + state + "] at (" + btn.getX() + ", " + btn.getY() + ")");
                    }
                } else if (obj instanceof EditBox edit) {
                    String value = edit.getValue();
                    String hint = componentToColorString(edit.getMessage());
                    widgets.add("Text Field: " + (hint.isEmpty() ? "Input" : hint) + " = \"" + value + "\"");
                } else if (obj instanceof AbstractWidget w) {
                    String msg = componentToColorString(w.getMessage());
                    if (!msg.isEmpty()) {
                        widgets.add("Widget: " + msg + " at (" + w.getX() + ", " + w.getY() + ")");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not access widgets: " + e.getMessage());
        }

        if (!widgets.isEmpty()) {
            sb.append("\n=== UI ELEMENTS ===\n");
            for (String w : widgets) {
                sb.append(w).append("\n");
            }
        }

        return sb.toString();
    }

    private void copyToClipboard(String content) {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(content);
        } catch (Exception e) {
            LOGGER.debug("Could not copy to clipboard: " + e.getMessage());
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
