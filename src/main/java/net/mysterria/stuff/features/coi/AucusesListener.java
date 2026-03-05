package net.mysterria.stuff.features.coi;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AucusesListener implements Listener {

    private static final long BASE_COOLDOWN_TIME = 30000;
    private static final long MAX_COOLDOWN_TIME = 180000;
    private static final long COOLDOWN_RESET_TIME = 90000;

    private static final int FIRE_TICKS_NORMAL = 8 * 20;
    private static final int BURN_RADIUS = 5;
    private static final int BURN_RESTORE_MIN_TICKS = 200;
    private static final int BURN_RESTORE_EXTRA_TICKS = 60;

    private final MysterriaStuff plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> usageCount;
    private final Random random = new Random();

    public AucusesListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.usageCount = new HashMap<>();
    }

    @EventHandler
    public void onAucusesChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!player.getWorld().getName().equals("world")) return;

        String serializedMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        String lowerMessage = serializedMessage.toLowerCase();

        boolean isUppercase = serializedMessage.contains("AUCUSES");
        boolean containsAucuses = lowerMessage.contains("aucuses");

        if (!containsAucuses) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastUsed = cooldowns.get(playerId);
            int uses = usageCount.getOrDefault(playerId, 0);
            long calculatedCooldown = Math.min(BASE_COOLDOWN_TIME * (long) Math.pow(2, uses), MAX_COOLDOWN_TIME);
            long timeLeft = calculatedCooldown - (currentTime - lastUsed);

            if (timeLeft > 0) {
                player.sendMessage(Component.text("Aucuses is still burning within. Wait " + String.format("%.1f", timeLeft / 1000.0) + " more seconds.")
                        .color(NamedTextColor.RED));
                usageCount.put(playerId, uses + 1);
                return;
            }

            if (currentTime - lastUsed > COOLDOWN_RESET_TIME) {
                usageCount.put(playerId, 0);
            }
        }

        cooldowns.put(playerId, currentTime);
        usageCount.put(playerId, usageCount.getOrDefault(playerId, 0) + 1);

        if (isUppercase) {
            summonFullWrath(player);
        } else {
            setAflame(player);
        }
    }

    private void setAflame(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setFireTicks(FIRE_TICKS_NORMAL);
            }
        }.runTask(plugin);
    }

    private void summonFullWrath(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.getWorld().strikeLightning(player.getLocation());
                player.setFireTicks(FIRE_TICKS_NORMAL * 2);
                burnNearbyBlocks(player);
            }
        }.runTask(plugin);
    }

    private void burnNearbyBlocks(Player player) {
        Location center = player.getLocation();
        Map<Location, BlockData> savedBlocks = new LinkedHashMap<>();

        for (int x = -BURN_RADIUS; x <= BURN_RADIUS; x++) {
            for (int y = -BURN_RADIUS; y <= BURN_RADIUS; y++) {
                for (int z = -BURN_RADIUS; z <= BURN_RADIUS; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    Material burnt = getBurntVariant(block.getType());
                    if (burnt != null) {
                        savedBlocks.put(block.getLocation(), block.getBlockData());
                        block.setType(burnt, false);
                    }
                }
            }
        }

        if (savedBlocks.isEmpty()) return;

        int restoreDelay = BURN_RESTORE_MIN_TICKS + random.nextInt(BURN_RESTORE_EXTRA_TICKS);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Location, BlockData> entry : savedBlocks.entrySet()) {
                    entry.getKey().getBlock().setBlockData(entry.getValue(), false);
                }
            }
        }.runTaskLater(plugin, restoreDelay);
    }

    private Material getBurntVariant(Material material) {
        if (material == Material.GRASS_BLOCK) return Material.COARSE_DIRT;
        if (Tag.LEAVES.isTagged(material)) return Material.AIR;
        if (Tag.FLOWERS.isTagged(material)) return Material.AIR;
        String name = material.name();
        if (name.equals("SHORT_GRASS") || name.equals("FERN")) return Material.AIR;
        return null;
    }
}
