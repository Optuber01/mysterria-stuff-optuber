package net.mysterria.stuff.features.coi;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.features.zones.CoiZoneManager;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class StianoListener implements Listener {

    private static final long BASE_COOLDOWN_TIME = 25000;
    private static final long MAX_COOLDOWN_TIME = 150000;
    private static final long COOLDOWN_RESET_TIME = 75000;

    private static final int HASTE_DURATION_TICKS = 30 * 20;
    private static final int HASTE_DURATION_CAPS_TICKS = 60 * 20;
    private static final int SPEED_DURATION_TICKS = 60 * 20;
    private static final int LIGHTNING_SPARK_COUNT = 5;

    private final MysterriaStuff plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> usageCount;
    private final Random random = new Random();

    public StianoListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.usageCount = new HashMap<>();
    }

    @EventHandler
    public void onStianoChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!player.getWorld().getName().equals("world")) return;

        CoiZoneManager zoneManager = plugin.getCoiZoneManager();
        if (zoneManager != null && zoneManager.isEnabled() && !zoneManager.isInAnyZone(player.getUniqueId())) return;

        String serializedMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        String lowerMessage = serializedMessage.toLowerCase();

        boolean isUppercase = serializedMessage.contains("STIANO");
        boolean containsStiano = lowerMessage.contains("stiano");

        if (!containsStiano) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastUsed = cooldowns.get(playerId);
            int uses = usageCount.getOrDefault(playerId, 0);
            long calculatedCooldown = Math.min(BASE_COOLDOWN_TIME * (long) Math.pow(2, uses), MAX_COOLDOWN_TIME);
            long timeLeft = calculatedCooldown - (currentTime - lastUsed);

            if (timeLeft > 0) {
                player.sendMessage(Component.text("Stiano's forge is still cooling. Wait " + String.format("%.1f", timeLeft / 1000.0) + " more seconds.")
                        .color(NamedTextColor.YELLOW));
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
            grantMasterCraftsman(player);
        } else {
            grantCraftsmansBlessing(player);
        }
    }

    private void grantCraftsmansBlessing(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, HASTE_DURATION_TICKS, 1));
            }
        }.runTask(plugin);
    }

    private void grantMasterCraftsman(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, HASTE_DURATION_CAPS_TICKS, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_DURATION_TICKS, 1));

                for (int i = 0; i < LIGHTNING_SPARK_COUNT; i++) {
                    final int delay = i * 4;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!player.isOnline()) return;
                            double xOffset = random.nextDouble() * 4 - 2;
                            double zOffset = random.nextDouble() * 4 - 2;
                            player.getWorld().strikeLightningEffect(
                                    player.getLocation().add(xOffset, 0, zOffset)
                            );
                        }
                    }.runTaskLater(plugin, delay);
                }
            }
        }.runTask(plugin);
    }
}
