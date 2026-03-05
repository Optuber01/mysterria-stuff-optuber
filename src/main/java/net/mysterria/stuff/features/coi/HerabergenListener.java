package net.mysterria.stuff.features.coi;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HerabergenListener implements Listener {

    private static final long BASE_COOLDOWN_TIME = 35000;
    private static final long MAX_COOLDOWN_TIME = 210000;
    private static final long COOLDOWN_RESET_TIME = 100000;

    private static final int NIGHT_VISION_TICKS = 60 * 20;
    private static final int LEVITATION_TICKS = 3 * 20;
    private static final int SLOW_FALLING_TICKS = 10 * 20;
    private static final int GLOWING_TICKS = 30 * 20;
    private static final double GLOW_RADIUS = 15.0;

    private final MysterriaStuff plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> usageCount;

    public HerabergenListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.usageCount = new HashMap<>();
    }

    @EventHandler
    public void onHerabergenChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!player.getWorld().getName().equals("world")) return;

        String serializedMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        String lowerMessage = serializedMessage.toLowerCase();

        boolean isUppercase = serializedMessage.contains("HERABERGEN");
        boolean containsHerabergen = lowerMessage.contains("herabergen");

        if (!containsHerabergen) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastUsed = cooldowns.get(playerId);
            int uses = usageCount.getOrDefault(playerId, 0);
            long calculatedCooldown = Math.min(BASE_COOLDOWN_TIME * (long) Math.pow(2, uses), MAX_COOLDOWN_TIME);
            long timeLeft = calculatedCooldown - (currentTime - lastUsed);

            if (timeLeft > 0) {
                player.sendMessage(Component.text("Herabergen's wisdom is beyond your reach for another " + String.format("%.1f", timeLeft / 1000.0) + " seconds.")
                        .color(NamedTextColor.GOLD));
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
            grantDivineWisdom(player);
        } else {
            grantKnowledge(player);
        }
    }

    private void grantKnowledge(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, NIGHT_VISION_TICKS, 1));
                spawnXpOrbs(player, 5, 5);
            }
        }.runTask(plugin);
    }

    private void grantDivineWisdom(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, NIGHT_VISION_TICKS, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, LEVITATION_TICKS, 2));

                for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), GLOW_RADIUS, GLOW_RADIUS, GLOW_RADIUS)) {
                    if (entity instanceof LivingEntity living && !entity.equals(player)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, GLOWING_TICKS, 0));
                    }
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALLING_TICKS, 0));
                            spawnXpOrbs(player, 10, 10);
                        }
                    }
                }.runTaskLater(plugin, LEVITATION_TICKS);
            }
        }.runTask(plugin);
    }

    private void spawnXpOrbs(Player player, int count, int xpPerOrb) {
        for (int i = 0; i < count; i++) {
            player.getWorld().spawn(player.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(xpPerOrb));
        }
    }
}
