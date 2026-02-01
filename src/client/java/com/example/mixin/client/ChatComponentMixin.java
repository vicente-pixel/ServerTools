package com.example.mixin.client;

import com.example.ChatCopy;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component modifyMessage(Component message) {
        // Check if already processed (prevent double [C])
        String plainText = message.getString();
        if (plainText.endsWith("[C]")) {
            return message;
        }

        // Convert the ORIGINAL message to legacy format BEFORE adding [C]
        String legacyFormatted = ChatCopy.componentToLegacy(message);

        // Remove any accidental [C] from the legacy string (safety check)
        if (legacyFormatted.endsWith(" [C]")) {
            legacyFormatted = legacyFormatted.substring(0, legacyFormatted.length() - 4);
        }

        // Create the [C] button with copy-to-clipboard action
        MutableComponent copyButton = Component.literal(" [C]")
            .withStyle(Style.EMPTY
                .withColor(0x55FF55) // Green color
                .withClickEvent(new ClickEvent.CopyToClipboard(legacyFormatted))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§aClick to copy with formatting")))
            );

        // Append the copy button to the original message
        return Component.empty().append(message).append(copyButton);
    }
}
