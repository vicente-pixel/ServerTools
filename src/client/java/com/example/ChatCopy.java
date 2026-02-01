package com.example;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles copying chat messages with formatting codes.
 *
 * While in chat screen:
 * - CMD/CTRL + C = copy most recent message
 * - CMD/CTRL + 1-9 = copy specific message (1=newest, 9=9th newest)
 */
public class ChatCopy {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatCopy");
    private static ChatCopy instance;

    private boolean wasKeyPressed = false;

    private ChatCopy() {}

    public static ChatCopy getInstance() {
        if (instance == null) {
            instance = new ChatCopy();
        }
        return instance;
    }

    public void onTick() {
        Minecraft mc = Minecraft.getInstance();

        // Only active when chat screen is open
        if (!(mc.screen instanceof ChatScreen)) {
            wasKeyPressed = false;
            return;
        }

        long window = GLFW.glfwGetCurrentContext();

        // Check for CMD (Mac) or CTRL (Windows/Linux)
        boolean cmdOrCtrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (!cmdOrCtrl) {
            wasKeyPressed = false;
            return;
        }

        // Check which key is pressed
        int messageIndex = -1;

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS) {
            messageIndex = 0; // Most recent
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_1) == GLFW.GLFW_PRESS) {
            messageIndex = 0;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_2) == GLFW.GLFW_PRESS) {
            messageIndex = 1;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_3) == GLFW.GLFW_PRESS) {
            messageIndex = 2;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_4) == GLFW.GLFW_PRESS) {
            messageIndex = 3;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_5) == GLFW.GLFW_PRESS) {
            messageIndex = 4;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_6) == GLFW.GLFW_PRESS) {
            messageIndex = 5;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_7) == GLFW.GLFW_PRESS) {
            messageIndex = 6;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_8) == GLFW.GLFW_PRESS) {
            messageIndex = 7;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_9) == GLFW.GLFW_PRESS) {
            messageIndex = 8;
        }

        if (messageIndex >= 0 && !wasKeyPressed) {
            copyMessage(mc, messageIndex);
            wasKeyPressed = true;
        } else if (messageIndex < 0) {
            wasKeyPressed = false;
        }
    }

    private void copyMessage(Minecraft mc, int index) {
        ChatComponent chat = mc.gui.getChat();

        if (!(chat instanceof ChatComponentAccessor accessor)) {
            sendActionBar(mc, "§c[Chat Copy] Cannot access chat");
            return;
        }

        List<GuiMessage> allMessages = accessor.getAllMessages();
        if (allMessages == null || allMessages.isEmpty()) {
            sendActionBar(mc, "§c[Chat Copy] No messages");
            return;
        }

        if (index >= allMessages.size()) {
            sendActionBar(mc, "§c[Chat Copy] Only " + allMessages.size() + " messages");
            return;
        }

        GuiMessage msg = allMessages.get(index);
        Component content = msg.content();

        // Convert to legacy format codes
        String formatted = componentToLegacy(content);

        // Copy to clipboard
        mc.keyboardHandler.setClipboard(formatted);

        // Show preview (truncated)
        String preview = formatted.length() > 30 ? formatted.substring(0, 30) + "..." : formatted;
        sendActionBar(mc, "§a[Chat Copy] #" + (index + 1) + ": " + preview);
        LOGGER.info("Copied message #{}: {}", index + 1, formatted);
    }

    private void sendActionBar(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.execute(() -> {
                mc.player.displayClientMessage(Component.literal(message), true);
            });
        }
    }

    /**
     * Converts a Component to legacy format codes (&c, &l, etc.)
     */
    public static String componentToLegacy(Component component) {
        StringBuilder result = new StringBuilder();
        appendComponentLegacy(component, result);
        return result.toString();
    }

    private static void appendComponentLegacy(Component component, StringBuilder result) {
        Style style = component.getStyle();

        // Get the text content
        String text = "";
        var contents = component.getContents();
        if (contents instanceof PlainTextContents.LiteralContents literal) {
            text = literal.text();
        }

        // Only add format codes if there's actual non-whitespace text
        if (!text.isEmpty() && !text.isBlank()) {
            // Add color code
            TextColor color = style.getColor();
            if (color != null) {
                String colorCode = getColorCode(color);
                if (colorCode != null) {
                    result.append(colorCode);
                }
            }

            // Add formatting codes
            if (style.isBold()) {
                result.append("&l");
            }
            if (style.isItalic()) {
                result.append("&o");
            }
            if (style.isUnderlined()) {
                result.append("&n");
            }
            if (style.isStrikethrough()) {
                result.append("&m");
            }
            if (style.isObfuscated()) {
                result.append("&k");
            }

            result.append(text);
        } else if (!text.isEmpty()) {
            // Just whitespace - add without color codes
            result.append(text);
        }

        // Process siblings
        for (Component sibling : component.getSiblings()) {
            appendComponentLegacy(sibling, result);
        }
    }

    private static String getColorCode(TextColor color) {
        int value = color.getValue();

        // Standard Minecraft color codes
        if (value == 0x000000) return "&0";
        if (value == 0x0000AA) return "&1";
        if (value == 0x00AA00) return "&2";
        if (value == 0x00AAAA) return "&3";
        if (value == 0xAA0000) return "&4";
        if (value == 0xAA00AA) return "&5";
        if (value == 0xFFAA00) return "&6";
        if (value == 0xAAAAAA) return "&7";
        if (value == 0x555555) return "&8";
        if (value == 0x5555FF) return "&9";
        if (value == 0x55FF55) return "&a";
        if (value == 0x55FFFF) return "&b";
        if (value == 0xFF5555) return "&c";
        if (value == 0xFF55FF) return "&d";
        if (value == 0xFFFF55) return "&e";
        if (value == 0xFFFFFF) return "&f";

        // Hex colors
        return String.format("&#%06X", value);
    }

    /**
     * Interface for accessing ChatComponent internals via mixin
     */
    public interface ChatComponentAccessor {
        List<GuiMessage> getAllMessages();
    }
}
