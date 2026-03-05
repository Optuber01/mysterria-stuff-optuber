package net.mysterria.stuff.features.coi;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mysterria.stuff.MysterriaStuff;
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

public class AmanisesListener implements Listener {

    private static final long BASE_COOLDOWN_TIME = 25000;
    private static final long MAX_COOLDOWN_TIME = 150000;
    private static final long COOLDOWN_RESET_TIME = 75000;

    private static final int INVISIBILITY_DURATION_TICKS = 30 * 20;
    private static final int SPECTATOR_MIN_SECONDS = 5;
    private static final int SPECTATOR_MAX_SECONDS = 8;

    private final MysterriaStuff plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> usageCount;
    private final Random random = new Random();

    public AmanisesListener(MysterriaStuff plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.usageCount = new HashMap<>();
    }

    @EventHandler
    public void onAmanisesChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!player.getWorld().getName().equals("world")) return;

        String serializedMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        String lowerMessage = serializedMessage.toLowerCase();

        boolean isUppercase = serializedMessage.contains("AMANISES");
        boolean containsAmanises = lowerMessage.contains("amanises");

        if (!containsAmanises) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastUsed = cooldowns.get(playerId);
            int uses = usageCount.getOrDefault(playerId, 0);
            long calculatedCooldown = Math.min(BASE_COOLDOWN_TIME * (long) Math.pow(2, uses), MAX_COOLDOWN_TIME);
            long timeLeft = calculatedCooldown - (currentTime - lastUsed);

            if (timeLeft > 0) {
                player.sendMessage(Component.text("Amanises does not answer yet. Wait " + String.format("%.1f", timeLeft / 1000.0) + " more seconds.")
                        .color(NamedTextColor.DARK_PURPLE));
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
            enterSpectatorMode(player);
        } else {
            grantInvisibility(player);
        }
    }

    private void grantInvisibility(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, INVISIBILITY_DURATION_TICKS, 0));
            }
        }.runTask(plugin);
    }

    private void enterSpectatorMode(Player player) {
        int durationSeconds = SPECTATOR_MIN_SECONDS + random.nextInt(SPECTATOR_MAX_SECONDS - SPECTATOR_MIN_SECONDS + 1);
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.SPECTATOR);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                    }
                }.runTaskLater(plugin, durationSeconds * 20L);
            }
        }.runTask(plugin);
    }
}
