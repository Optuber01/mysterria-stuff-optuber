package net.mysterria.stuff.features.zones;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CoiZoneManager implements Listener {

    private final MysterriaStuff plugin;
    private final List<CoiZone> zones;
    private final Set<UUID> playersInZone = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> lightningTasks = new HashMap<>();
    private final Random random = new Random();

    public CoiZoneManager(MysterriaStuff plugin, List<CoiZone> zones) {
        this.plugin = plugin;
        this.zones = zones;
        PrettyLogger.debug("CoiZoneManager loaded " + zones.size() + " zone(s)");
    }

    public boolean isEnabled() {
        return !zones.isEmpty();
    }

    public boolean isInAnyZone(UUID playerId) {
        return playersInZone.contains(playerId);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        checkZoneTransition(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        checkZoneTransition(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        playersInZone.remove(id);
        cancelLightning(id);
    }

    private void checkZoneTransition(Player player, Location to) {
        if (to == null) return;
        UUID id = player.getUniqueId();
        boolean wasInZone = playersInZone.contains(id);
        CoiZone newZone = getZoneAt(to);
        boolean isInZone = newZone != null;

        if (!wasInZone && isInZone) {
            playersInZone.add(id);
            if (newZone.isAlwaysNight()) {
                player.setPlayerTime(18000, false);
            }
            if (newZone.isLightningEffects()) {
                startLightning(player, newZone);
            }
            PrettyLogger.debug(player.getName() + " entered CoI zone: " + newZone.getName());
        } else if (wasInZone && !isInZone) {
            playersInZone.remove(id);
            player.resetPlayerTime();
            cancelLightning(id);
            PrettyLogger.debug(player.getName() + " exited CoI zone");
        }
    }

    private CoiZone getZoneAt(Location location) {
        for (CoiZone zone : zones) {
            if (zone.contains(location)) return zone;
        }
        return null;
    }

    private void startLightning(Player player, CoiZone zone) {
        UUID id = player.getUniqueId();
        cancelLightning(id);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelLightning(id);
                return;
            }
            Location loc = player.getLocation();
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * zone.getLightningRadius();
            double x = loc.getX() + Math.cos(angle) * dist;
            double z = loc.getZ() + Math.sin(angle) * dist;
            int highY = loc.getWorld().getHighestBlockYAt((int) x, (int) z);
            Location strike = new Location(loc.getWorld(), x, highY, z);
            loc.getWorld().strikeLightningEffect(strike);
        }, zone.getLightningIntervalTicks(), zone.getLightningIntervalTicks());
        lightningTasks.put(id, task);
    }

    private void cancelLightning(UUID id) {
        BukkitTask task = lightningTasks.remove(id);
        if (task != null) task.cancel();
    }

    public void shutdown() {
        lightningTasks.values().forEach(BukkitTask::cancel);
        lightningTasks.clear();
        for (UUID id : playersInZone) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.resetPlayerTime();
        }
        playersInZone.clear();
    }
}
