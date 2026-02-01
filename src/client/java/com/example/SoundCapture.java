package com.example;

import com.example.mixin.client.SoundEngineAccessor;
import com.example.mixin.client.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SoundCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("SoundCapture");
    private static SoundCapture instance;

    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private final AtomicBoolean isCapturingSequence = new AtomicBoolean(false);

    private final List<GameSoundEvent> currentSequence = new CopyOnWriteArrayList<>();
    private final Set<Integer> processedSoundHashes = new HashSet<>();
    private long lastCleanupTime = 0;

    private SoundCapture() {}

    public static SoundCapture getInstance() {
        if (instance == null) {
            instance = new SoundCapture();
        }
        return instance;
    }

    public void toggleSoundCapture() {
        if (isCapturing.get()) {
            stopCapture();
        } else {
            startCapture();
        }
    }

    public void toggleSequenceCapture() {
        if (isCapturingSequence.get()) {
            stopSequenceCapture();
        } else {
            startSequenceCapture();
        }
    }

    private void startCapture() {
        if (isCapturing.compareAndSet(false, true)) {
            processedSoundHashes.clear();
            sendChatMessage("§a[Sound Capture] Started listening for game sounds...");
            sendChatMessage("§7Press K again to stop.");
            LOGGER.info("Sound capture started");
        }
    }

    private void stopCapture() {
        if (isCapturing.compareAndSet(true, false)) {
            sendChatMessage("§c[Sound Capture] Stopped listening for game sounds.");
            LOGGER.info("Sound capture stopped");
        }
    }

    private void startSequenceCapture() {
        if (isCapturingSequence.compareAndSet(false, true)) {
            currentSequence.clear();
            processedSoundHashes.clear();
            sendChatMessage("§a[Sequence Capture] Started recording sound sequence...");
            sendChatMessage("§7Press N again to stop and save the sequence.");
            LOGGER.info("Sequence capture started");
        }
    }

    private void stopSequenceCapture() {
        if (isCapturingSequence.compareAndSet(true, false)) {
            if (!currentSequence.isEmpty()) {
                saveSequenceToFile();
                sendChatMessage("§a[Sequence Capture] Saved " + currentSequence.size() + " sounds to file!");
            } else {
                sendChatMessage("§e[Sequence Capture] No sounds detected in sequence.");
            }
            LOGGER.info("Sequence capture stopped");
        }
    }

    /**
     * Called each client tick to check for new sounds
     */
    public void onTick() {
        if (!isCapturing.get() && !isCapturingSequence.get()) {
            return;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null) {
                return;
            }

            // Clean up old hashes periodically
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCleanupTime > 2000) {
                processedSoundHashes.clear();
                lastCleanupTime = currentTime;
            }

            SoundManager soundManager = mc.getSoundManager();

            // Access the SoundEngine directly using the access widener
            SoundEngine soundEngine = ((SoundManagerAccessor) soundManager).getSoundEngine();
            if (soundEngine == null) {
                return;
            }

            // Get the playing sounds map
            Map<SoundInstance, ?> playingSounds = ((SoundEngineAccessor) soundEngine).getInstanceToChannel();
            if (playingSounds == null || playingSounds.isEmpty()) {
                return;
            }

            for (SoundInstance sound : playingSounds.keySet()) {
                if (sound == null) continue;

                try {
                    int soundHash = System.identityHashCode(sound);

                    // Skip if already processed
                    if (processedSoundHashes.contains(soundHash)) {
                        continue;
                    }
                    processedSoundHashes.add(soundHash);

                    if (sound.getSound() == null || sound.getSound().getLocation() == null) {
                        continue;
                    }

                    String sourceName = sound.getSource() != null ? sound.getSource().getName() : "unknown";

                    // Skip music sounds
                    if (sourceName.equalsIgnoreCase("music") || sourceName.equalsIgnoreCase("records")) {
                        continue;
                    }

                    String soundName = sound.getSound().getLocation().toString();
                    String soundId = getSoundId(soundName);
                    float volume = sound.getVolume();
                    float pitch = sound.getPitch();
                    double x = sound.getX();
                    double y = sound.getY();
                    double z = sound.getZ();

                    processSound(soundName, soundId, sourceName, x, y, z, volume, pitch);
                } catch (Exception e) {
                    // Ignore individual sound errors
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking sounds: " + e.getMessage());
        }
    }

    private void processSound(String soundName, String soundId, String sourceName,
                              double x, double y, double z, float volume, float pitch) {

        if (isCapturing.get()) {
            String message = String.format(
                "§b[Sound] §f%s §7| Vol: §a%.1f §7| Pitch: §d%.2f",
                soundId, volume, pitch
            );
            sendChatMessage(message);
        }

        if (isCapturingSequence.get()) {
            long currentTime = System.currentTimeMillis();
            GameSoundEvent event = new GameSoundEvent(
                currentTime,
                soundName,
                soundId,
                sourceName,
                x, y, z,
                volume,
                pitch
            );
            currentSequence.add(event);

            sendChatMessage(String.format(
                "§d[Seq #%d] §f%s §7(%.2f pitch)",
                currentSequence.size(), soundId, pitch
            ));
        }
    }

    private String getSoundId(String fullName) {
        // Extract just the ID from something like "minecraft:entity.player.hurt"
        // Returns: "entity.player.hurt"
        if (fullName.contains(":")) {
            return fullName.substring(fullName.indexOf(":") + 1);
        }
        return fullName;
    }

    private void saveSequenceToFile() {
        try {
            File sequenceDir = new File("sound_sequences");
            if (!sequenceDir.exists()) {
                sequenceDir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File sequenceFile = new File(sequenceDir, "sequence_" + timestamp + ".txt");

            try (PrintWriter writer = new PrintWriter(new FileWriter(sequenceFile))) {
                writer.println("=== Game Sound Sequence Recording ===");
                writer.println("Recorded: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.println("Total sounds: " + currentSequence.size());
                writer.println();
                writer.println("--- Sequence Pattern ---");

                // Write the sequence pattern (simple names only)
                StringBuilder pattern = new StringBuilder();
                for (GameSoundEvent event : currentSequence) {
                    pattern.append(event.soundId).append(" ");
                }
                writer.println(pattern.toString().trim());
                writer.println();

                writer.println("--- Detailed Log ---");
                writer.println(String.format("%-5s | %-9s | %-30s | %-10s | %-6s | %-5s",
                    "Index", "Time (ms)", "Sound", "Source", "Volume", "Pitch"));
                writer.println("-".repeat(80));

                long firstTime = currentSequence.isEmpty() ? 0 : currentSequence.get(0).timestamp;
                int index = 1;
                for (GameSoundEvent event : currentSequence) {
                    writer.printf("%-5d | %-9d | %-30s | %-10s | %-6.2f | %-5.2f%n",
                        index++,
                        event.timestamp - firstTime,
                        event.soundId,
                        event.sourceName,
                        event.volume,
                        event.pitch
                    );
                }

                writer.println();
                writer.println("--- Timing Analysis ---");
                if (currentSequence.size() > 1) {
                    List<Long> gaps = new ArrayList<>();
                    for (int i = 1; i < currentSequence.size(); i++) {
                        gaps.add(currentSequence.get(i).timestamp - currentSequence.get(i - 1).timestamp);
                    }
                    double avgGap = gaps.stream().mapToLong(Long::longValue).average().orElse(0);
                    long minGap = gaps.stream().mapToLong(Long::longValue).min().orElse(0);
                    long maxGap = gaps.stream().mapToLong(Long::longValue).max().orElse(0);

                    writer.printf("Average gap between sounds: %.1f ms%n", avgGap);
                    writer.printf("Minimum gap: %d ms%n", minGap);
                    writer.printf("Maximum gap: %d ms%n", maxGap);
                }

                writer.println();
                writer.println("--- Full Sound Names ---");
                for (GameSoundEvent event : currentSequence) {
                    writer.println(event.fullName);
                }
            }

            LOGGER.info("Saved sequence to: " + sequenceFile.getAbsolutePath());
            sendChatMessage("§7Saved to: sound_sequences/" + sequenceFile.getName());
        } catch (IOException e) {
            sendChatMessage("§c[Sequence Capture] Failed to save file: " + e.getMessage());
            LOGGER.error("Failed to save sequence file", e);
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

    public boolean isCapturing() {
        return isCapturing.get();
    }

    public boolean isCapturingSequence() {
        return isCapturingSequence.get();
    }

    private static class GameSoundEvent {
        final long timestamp;
        final String fullName;
        final String soundId;
        final String sourceName;
        final double x, y, z;
        final float volume;
        final float pitch;

        GameSoundEvent(long timestamp, String fullName, String soundId, String sourceName,
                       double x, double y, double z, float volume, float pitch) {
            this.timestamp = timestamp;
            this.fullName = fullName;
            this.soundId = soundId;
            this.sourceName = sourceName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
}
