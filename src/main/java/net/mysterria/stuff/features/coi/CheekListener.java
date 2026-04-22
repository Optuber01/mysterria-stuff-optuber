package net.mysterria.stuff.features.coi;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.features.zones.CoiZoneManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

public class CheekListener implements Listener {

    private static final long BASE_COOLDOWN_TIME = 20000;
    private static final long MAX_COOLDOWN_TIME = 120000;
    private static final long COOLDOWN_RESET_TIME = 60000;

    private static final int CHAOS_EFFECT_DURATION_TICKS = 20 * 20;
    private static final double TELEPORT_MIN_DISTANCE = 15.0;
    private static final double TELEPORT_MAX_DISTANCE = 40.0;

    private static final PotionEffectType[] CHAOS_POOL = {
            PotionEffectType.SPEED,
            PotionEffectType.SLOWNESS,
            PotionEffectType.HASTE,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.STRENGTH,
            PotionEffectType.WEAKNESS,
            PotionEffectType.JUMP_BOOST,
            PotionEffectType.LEVITATION,
            PotionEffectType.REGENERATION,
            PotionEffectType.POISON,
            PotionEffectType.GLOWING,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.DARKNESS
    };

    private final MysterriaStuff plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> usageCount;
    private final Random random = new Random();

    public CheekListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.usageCount = new HashMap<>();
    }

    @EventHandler
    public void onCheekChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!player.getWorld().getName().equals("world")) return;

        CoiZoneManager zoneManager = plugin.getCoiZoneManager();
        if (zoneManager != null && zoneManager.isEnabled() && !zoneManager.isInAnyZone(player.getUniqueId())) return;

        String serializedMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        String lowerMessage = serializedMessage.toLowerCase();

        boolean isUppercase = serializedMessage.contains("CHEEK");
        boolean containsCheek = lowerMessage.contains("cheek");

        if (!containsCheek) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastUsed = cooldowns.get(playerId);
            int uses = usageCount.getOrDefault(playerId, 0);
            long calculatedCooldown = Math.min(BASE_COOLDOWN_TIME * (long) Math.pow(2, uses), MAX_COOLDOWN_TIME);
            long timeLeft = calculatedCooldown - (currentTime - lastUsed);

            if (timeLeft > 0) {
                player.sendMessage(Component.text("The Cheek already toys with your fate. The chaos lingers for " + String.format("%.1f", timeLeft / 1000.0) + " more seconds.")
                        .color(NamedTextColor.DARK_GRAY));
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
            unleashCatastrophe(player);
        } else {
            spreadChaos(player);
        }
    }

    private void spreadChaos(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    PotionEffectType type = CHAOS_POOL[random.nextInt(CHAOS_POOL.length)];
                    player.addPotionEffect(new PotionEffect(type, CHAOS_EFFECT_DURATION_TICKS, random.nextInt(2)));
                }
            }
        }.runTask(plugin);
    }

    private void unleashCatastrophe(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 4; i++) {
                    PotionEffectType type = CHAOS_POOL[random.nextInt(CHAOS_POOL.length)];
                    player.addPotionEffect(new PotionEffect(type, CHAOS_EFFECT_DURATION_TICKS, random.nextInt(3)));
                }

                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 5 * 20, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 8 * 20, 0));

                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = TELEPORT_MIN_DISTANCE + random.nextDouble() * (TELEPORT_MAX_DISTANCE - TELEPORT_MIN_DISTANCE);
                double newX = player.getLocation().getX() + Math.cos(angle) * distance;
                double newZ = player.getLocation().getZ() + Math.sin(angle) * distance;
                int newY = player.getWorld().getHighestBlockYAt((int) newX, (int) newZ) + 1;

                Location destination = new Location(
                        player.getWorld(), newX, newY, newZ,
                        player.getLocation().getYaw(), player.getLocation().getPitch()
                );
                player.teleport(destination);
            }
        }.runTask(plugin);
    }
}
