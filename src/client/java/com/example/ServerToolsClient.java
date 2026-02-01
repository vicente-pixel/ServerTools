package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerToolsClient implements ClientModInitializer {
    public static final String MOD_ID = "servertools";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping toggleDownloadKey;
    private static KeyMapping soundCaptureKey;
    private static KeyMapping sequenceCaptureKey;
    private static KeyMapping menuCaptureKey;
    private static KeyMapping mapCaptureKey;
    private static KeyMapping npcCaptureKey;
    private static KeyMapping headCaptureKey;
    private static KeyMapping armorStandCaptureKey;

    private static boolean jKeyWasPressed = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Server Tools mod initialized!");

        toggleDownloadKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.toggle",
                GLFW.GLFW_KEY_F9,
                Category.MISC
        ));

        soundCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.sound_capture",
                GLFW.GLFW_KEY_K,
                Category.MISC
        ));

        sequenceCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.sequence_capture",
                GLFW.GLFW_KEY_N,
                Category.MISC
        ));

        menuCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.menu_capture",
                GLFW.GLFW_KEY_J,
                Category.MISC
        ));

        mapCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.map_capture",
                GLFW.GLFW_KEY_H,
                Category.MISC
        ));

        npcCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.npc_capture",
                GLFW.GLFW_KEY_B,
                Category.MISC
        ));

        headCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.head_capture",
                GLFW.GLFW_KEY_G,
                Category.MISC
        ));

        armorStandCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.servertools.armorstand_capture",
                GLFW.GLFW_KEY_P,
                Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleDownloadKey.consumeClick()) {
                ChunkDownloader.getInstance().toggleDownload();
            }

            while (soundCaptureKey.consumeClick()) {
                SoundCapture.getInstance().toggleSoundCapture();
            }

            while (sequenceCaptureKey.consumeClick()) {
                SoundCapture.getInstance().toggleSequenceCapture();
            }

            while (menuCaptureKey.consumeClick()) {
                MenuCapture.getInstance().captureCurrentMenu();
            }

            while (mapCaptureKey.consumeClick()) {
                MapCapture.getInstance().captureMapArt();
            }

            while (npcCaptureKey.consumeClick()) {
                NPCCapture.getInstance().captureTargetedNPC();
            }

            while (headCaptureKey.consumeClick()) {
                HeadCapture.getInstance().toggleCapture();
            }

            while (armorStandCaptureKey.consumeClick()) {
                ArmorStandCapture.getInstance().toggleCapture();
            }

            // Check for J key while a CONTAINER screen is open (not inventory)
            if (client.screen instanceof AbstractContainerScreen<?>
                    && !(client.screen instanceof InventoryScreen)
                    && !(client.screen instanceof CreativeModeInventoryScreen)) {
                long window = GLFW.glfwGetCurrentContext();
                boolean jKeyPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS;
                if (jKeyPressed && !jKeyWasPressed) {
                    MenuCapture.getInstance().captureCurrentMenu();
                }
                jKeyWasPressed = jKeyPressed;
            } else {
                jKeyWasPressed = false;
            }

            // Check for sounds each tick
            SoundCapture.getInstance().onTick();

            // Check for chat copy (CMD/CTRL+C while hovering chat)
            ChatCopy.getInstance().onTick();

            // Check for armor stands with player heads
            HeadCapture.getInstance().onTick();

            // Check for armor stands with target texture
            ArmorStandCapture.getInstance().onTick();
        });
    }
}
