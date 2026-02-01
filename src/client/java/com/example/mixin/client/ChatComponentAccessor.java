package com.example.mixin.client;

import com.example.ChatCopy;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor extends ChatCopy.ChatComponentAccessor {

    @Accessor("allMessages")
    List<GuiMessage> getAllMessages();
}
