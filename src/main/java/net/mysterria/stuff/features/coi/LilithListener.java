package net.mysterria.stuff.features.coi;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.features.zones.CoiZoneManager;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LilithListener implements Listener {

    private static final long BASE_COOLDOWN_TIME = 40000;
    private static final long MAX_COOLDOWN_TIME = 240000;
    private static final long COOLDOWN_RESET_TIME = 120000;

    private static final int REGEN_DURATION_TICKS = 30 * 20;
    private static final int ABSORPTION_DURATION_TICKS = 30 * 20;
    private static final int REGEN_DURATION_CAPS_TICKS = 60 * 20;
    private static final int ABSORPTION_DURATION_CAPS_TICKS = 60 * 20;
    private static final int HEALTH_BOOST_DURATION_TICKS = 30 * 20;

    private final MysterriaStuff plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> usageCount;

    public LilithListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.usageCount = new HashMap<>();
    }

    @EventHandler
    public void onLilithChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!player.getWorld().getName().equals("world")) return;

        CoiZoneManager zoneManager = plugin.getCoiZoneManager();
        if (zoneManager != null && zoneManager.isEnabled() && !zoneManager.isInAnyZone(player.getUniqueId())) return;

        String serializedMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        String lowerMessage = serializedMessage.toLowerCase();

        boolean isUppercase = serializedMessage.contains("LILITH");
        boolean containsLilith = lowerMessage.contains("lilith");

        if (!containsLilith) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastUsed = cooldowns.get(playerId);
            int uses = usageCount.getOrDefault(playerId, 0);
            long calculatedCooldown = Math.min(BASE_COOLDOWN_TIME * (long) Math.pow(2, uses), MAX_COOLDOWN_TIME);
            long timeLeft = calculatedCooldown - (currentTime - lastUsed);

            if (timeLeft > 0) {
                player.sendMessage(Component.text("Lilith's blessing has not yet waned. Wait " + String.format("%.1f", timeLeft / 1000.0) + " more seconds.")
                        .color(NamedTextColor.DARK_RED));
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
            grantCrimsonMoonBlessing(player);
        } else {
            grantLifeBlessing(player);
        }
    }

    private void grantLifeBlessing(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGEN_DURATION_TICKS, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ABSORPTION_DURATION_TICKS, 1));
            }
        }.runTask(plugin);
    }

    private void grantCrimsonMoonBlessing(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(maxHealth);

                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGEN_DURATION_CAPS_TICKS, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ABSORPTION_DURATION_CAPS_TICKS, 3));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, HEALTH_BOOST_DURATION_TICKS, 1));

                spawnCrimsonMoonParticles(player);
            }
        }.runTask(plugin);
    }

    private void spawnCrimsonMoonParticles(Player player) {
        Location center = player.getLocation().add(0, 1, 0);
        Particle.DustOptions crimsonDust = new Particle.DustOptions(Color.fromRGB(180, 0, 30), 2.0f);

        for (int i = 0; i < 60; i++) {
            double angle = 2 * Math.PI * i / 60.0;
            double x = 2.5 * Math.cos(angle);
            double z = 2.5 * Math.sin(angle);
            player.getWorld().spawnParticle(Particle.DUST, center.clone().add(x, 0, z), 1, 0, 0, 0, 0, crimsonDust);
        }

        for (int i = 0; i < 40; i++) {
            double angle = 2 * Math.PI * i / 40.0;
            double x = 1.5 * Math.cos(angle);
            double z = 1.5 * Math.sin(angle);
            player.getWorld().spawnParticle(Particle.DUST, center.clone().add(x, 1.0, z), 1, 0, 0, 0, 0, crimsonDust);
        }
    }
}
