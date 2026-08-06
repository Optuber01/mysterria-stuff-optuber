package net.mysterria.stuff.features.entities;

import net.mysterria.stuff.MysterriaStuff;
import org.bukkit.World;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.Collection;
import java.util.List;

/**
 * Restores camel behavior after entity-management plugins strip their goals.
 */
public final class CamelAiListener implements Listener {

    private final MysterriaStuff plugin;

    public CamelAiListener(MysterriaStuff plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Camel camel) {
            restoreNextTick(List.of(camel));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        restoreNextTick(event.getEntities());
    }

    public void restoreAlreadyLoadedCamels() {
        for (World world : plugin.getServer().getWorlds()) {
            restoreNextTick(world.getEntitiesByClass(Camel.class));
        }
    }

    private void restoreNextTick(Collection<? extends Entity> entities) {
        List<Camel> camels = entities.stream()
                .filter(Camel.class::isInstance)
                .map(Camel.class::cast)
                .toList();

        if (camels.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> camels.forEach(this::restoreAi));
    }

    private void restoreAi(Camel camel) {
        if (!camel.isValid()) {
            return;
        }
        if (!camel.hasAI()) {
            camel.setAI(true);
        }
        if (!camel.isAware()) {
            camel.setAware(true);
        }
    }
}
