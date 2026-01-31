package com.example.mixin.client;

import com.example.WorldDownloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void onChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        WorldDownloader wdl = WorldDownloader.getInstance();
        if (!wdl.isDownloading()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        int chunkX = packet.getX();
        int chunkZ = packet.getZ();

        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        if (chunk != null) {
            wdl.saveChunk(chunk);
        }
    }
}
